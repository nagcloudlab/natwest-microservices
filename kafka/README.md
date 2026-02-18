# Kafka 3-Broker Cluster Setup (KRaft Mode)

This is a local 3-broker Kafka cluster using **KRaft** (no ZooKeeper required).

## Downloads

### Kafka

- **Source:** https://downloads.apache.org/kafka/3.7.2/kafka_2.13-3.7.2.tgz
- **Version:** 3.7.2 (Scala 2.13)

```bash
curl -L -o kafka.tgz "https://downloads.apache.org/kafka/3.7.2/kafka_2.13-3.7.2.tgz"
tar -xzf kafka.tgz
mv kafka_2.13-3.7.2 kafka
rm kafka.tgz
```

### Kafka UI

- **Source:** https://github.com/provectus/kafka-ui/releases/download/v0.7.2/kafka-ui-api-v0.7.2.jar
- **Version:** 0.7.2
- **GitHub:** https://github.com/provectus/kafka-ui (open-source Kafka web UI by Provectus)

```bash
mkdir kafka-ui && cd kafka-ui
curl -L "https://github.com/provectus/kafka-ui/releases/download/v0.7.2/kafka-ui-api-v0.7.2.jar" \
  -o kafka-ui-api-v0.7.2.jar
```

## Prerequisites

- Java 17+

---

## What's Installed

```
kafka/
  bin/                          # Kafka CLI tools
  config/
    broker-0.properties         # Broker 0 — port 9092
    broker-1.properties         # Broker 1 — port 9093
    broker-2.properties         # Broker 2 — port 9094
  data/
    broker-0/                   # Broker 0 log directory
    broker-1/                   # Broker 1 log directory
    broker-2/                   # Broker 2 log directory
  start-cluster.sh              # Start all 3 brokers
  stop-cluster.sh               # Stop all 3 brokers

kafka-ui/
  kafka-ui-api-v0.7.2.jar      # Kafka UI web application
  application.yml               # Configuration (port 8090)
```

---

## How It Works

### KRaft Mode (No ZooKeeper)

Kafka 3.3+ supports **KRaft** — a built-in consensus protocol that replaces ZooKeeper. Each broker runs both the `broker` and `controller` roles:

```
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│   BROKER 0      │  │   BROKER 1      │  │   BROKER 2      │
│                 │  │                 │  │                 │
│  Client: 9092   │  │  Client: 9093   │  │  Client: 9094   │
│  Controller:    │  │  Controller:    │  │  Controller:    │
│    19092        │  │    19093        │  │    19094        │
│                 │  │                 │  │                 │
│  Data:          │  │  Data:          │  │  Data:          │
│  data/broker-0/ │  │  data/broker-1/ │  │  data/broker-2/ │
└────────┬────────┘  └────────┬────────┘  └────────┬────────┘
         │                    │                    │
         └────────── Controller Quorum ────────────┘
```

### Port Map

| Broker | Client Port | Controller Port | Data Directory |
|--------|-------------|-----------------|----------------|
| 0      | 9092        | 19092           | data/broker-0/ |
| 1      | 9093        | 19093           | data/broker-1/ |
| 2      | 9094        | 19094           | data/broker-2/ |

### Cluster Settings

- **Partitions per topic:** 3 (default)
- **Replication factor:** 3 (every partition on every broker)
- **Min in-sync replicas:** 2 (tolerates 1 broker failure)
- **Log retention:** 1 hour (training environment)

---

## Quick Start

### 1. Start the Kafka Cluster

```bash
cd kafka
./start-cluster.sh
```

This will:
1. Generate a unique cluster ID
2. Format storage for all 3 brokers
3. Start all 3 brokers as daemon processes

### 2. Verify the Cluster

```bash
# List brokers
kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 2>/dev/null | head -3

# Create a topic
kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --create --topic order-events --partitions 3 --replication-factor 3

# Describe the topic
kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --describe --topic order-events
```

Expected output:
```
Topic: order-events  PartitionCount: 3  ReplicationFactor: 3
  Partition: 0  Leader: 2  Replicas: 2,0,1  Isr: 2,0,1
  Partition: 1  Leader: 0  Replicas: 0,1,2  Isr: 0,1,2
  Partition: 2  Leader: 1  Replicas: 1,2,0  Isr: 1,2,0
```

### 3. Produce and Consume (Manual Test)

```bash
# Terminal 1: Start a consumer
kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic order-events --from-beginning

# Terminal 2: Produce a message
echo '{"orderId":99,"consumerName":"Test"}' | \
  kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 \
  --topic order-events
```

### 4. Stop the Cluster

```bash
cd kafka
./stop-cluster.sh
```

---

## Clean Restart (Wipe All Data)

If you need a fresh cluster (e.g., after schema changes):

```bash
./stop-cluster.sh
rm -rf data/broker-0/* data/broker-1/* data/broker-2/*
./start-cluster.sh
```

---

## Useful CLI Commands

```bash
KAFKA=kafka/bin
BOOTSTRAP=localhost:9092

# List all topics
$KAFKA/kafka-topics.sh --bootstrap-server $BOOTSTRAP --list

# Describe a topic
$KAFKA/kafka-topics.sh --bootstrap-server $BOOTSTRAP --describe --topic order-events

# Read all messages from a topic
$KAFKA/kafka-console-consumer.sh --bootstrap-server $BOOTSTRAP \
  --topic order-events --from-beginning

# Check consumer group lag
$KAFKA/kafka-consumer-groups.sh --bootstrap-server $BOOTSTRAP \
  --describe --group notification-service

# Delete a topic
$KAFKA/kafka-topics.sh --bootstrap-server $BOOTSTRAP --delete --topic order-events
```

---

## Broker Configuration Explained

Each broker's `.properties` file contains:

```properties
# Identity
node.id=0                                    # Unique ID (0, 1, or 2)
process.roles=broker,controller              # Both roles in KRaft mode

# Listeners — separate ports for clients and controller
listeners=PLAINTEXT://:9092,CONTROLLER://:19092
advertised.listeners=PLAINTEXT://localhost:9092

# All 3 controllers vote together
controller.quorum.voters=0@localhost:19092,1@localhost:19093,2@localhost:19094

# Data
log.dirs=data/broker-0

# Replication
default.replication.factor=3
min.insync.replicas=2
```

---

# Kafka UI

## Download Source

- **Project:** Kafka UI by Provectus (open-source)
- **GitHub:** https://github.com/provectus/kafka-ui
- **Release:** v0.7.2
- **Direct download:** https://github.com/provectus/kafka-ui/releases/download/v0.7.2/kafka-ui-api-v0.7.2.jar

## Start Kafka UI

```bash
cd kafka-ui
java -Dspring.config.additional-location=application.yml \
     --add-opens java.rmi/javax.rmi.ssl=ALL-UNNAMED \
     -jar kafka-ui-api-v0.7.2.jar
```

**Access:** http://localhost:8090

> Note: Default Kafka UI port is 8080, but we use 8090 to avoid conflict with the monolith.

## Configuration

`kafka-ui/application.yml`:
```yaml
server:
  port: 8090

kafka:
  clusters:
    - name: local-cluster
      bootstrapServers: localhost:9092,localhost:9093,localhost:9094
```

## What You Can Do in Kafka UI

- **Brokers:** See all 3 brokers, their status, and partition distribution
- **Topics:** Browse topics, view messages, see partition layout
- **Consumers:** See consumer groups (e.g., `notification-service`) and their lag
- **Messages:** Read individual messages from any topic with JSON formatting

### Exploring the order-events Topic

1. Open http://localhost:8090
2. Click **Topics** -> **order-events**
3. Click **Messages** tab
4. You'll see the OrderCreated events as JSON:
   ```json
   {
     "orderId": 1,
     "consumerName": "Alice",
     "consumerContact": "9999888877",
     "restaurantName": "Mumbai Masala",
     "totalAmount": 980.00
   }
   ```
5. Click **Consumers** tab to see `notification-service` group and its offset/lag

---

# Start Everything (Full Stack)

Run these in order:

```bash
# 1. Kafka cluster (3 brokers)
cd kafka && ./start-cluster.sh && cd ..

# 2. Create topic (first time only)
kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --create --topic order-events --partitions 3 --replication-factor 3

# 3. Kafka UI (new terminal)
cd kafka-ui && java -Dspring.config.additional-location=application.yml \
  --add-opens java.rmi/javax.rmi.ssl=ALL-UNNAMED \
  -jar kafka-ui-api-v0.7.2.jar

# 4. Restaurant Service (new terminal)
cd iteration-2-notification-service/restaurant-service && mvn spring-boot:run

# 5. Notification Service (new terminal)
cd iteration-2-notification-service/notification-service && mvn spring-boot:run

# 6. Monolith (new terminal)
cd iteration-2-notification-service/ftgo-monolith && mvn spring-boot:run
```

## Port Summary

| Service              | Port  | URL                          |
|----------------------|-------|------------------------------|
| Monolith             | 8080  | http://localhost:8080        |
| Restaurant Service   | 8081  | http://localhost:8081        |
| Notification Service | 8082  | http://localhost:8082        |
| Kafka UI             | 8090  | http://localhost:8090        |
| Kafka Broker 0       | 9092  | (internal)                   |
| Kafka Broker 1       | 9093  | (internal)                   |
| Kafka Broker 2       | 9094  | (internal)                   |

## Stop Everything

```bash
# Stop app services (Ctrl+C in each terminal, or:)
kill $(lsof -ti:8080) $(lsof -ti:8081) $(lsof -ti:8082) $(lsof -ti:8090)

# Stop Kafka
cd kafka && ./stop-cluster.sh
```
