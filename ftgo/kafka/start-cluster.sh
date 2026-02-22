#!/bin/bash
# ============================================================
# Start 3-broker Kafka cluster (KRaft mode — no ZooKeeper)
# Brokers: localhost:9092, localhost:9093, localhost:9094
# ============================================================

KAFKA_HOME="$(cd "$(dirname "$0")" && pwd)"
CLUSTER_ID="$($KAFKA_HOME/bin/kafka-storage.sh random-uuid)"

echo "=== Kafka 3-Broker Cluster (KRaft) ==="
echo "Cluster ID: $CLUSTER_ID"
echo ""

# Format storage for all 3 brokers
echo ">>> Formatting storage for broker-0..."
$KAFKA_HOME/bin/kafka-storage.sh format -t $CLUSTER_ID -c $KAFKA_HOME/config/broker-0.properties --ignore-formatted 2>&1 | tail -1

echo ">>> Formatting storage for broker-1..."
$KAFKA_HOME/bin/kafka-storage.sh format -t $CLUSTER_ID -c $KAFKA_HOME/config/broker-1.properties --ignore-formatted 2>&1 | tail -1

echo ">>> Formatting storage for broker-2..."
$KAFKA_HOME/bin/kafka-storage.sh format -t $CLUSTER_ID -c $KAFKA_HOME/config/broker-2.properties --ignore-formatted 2>&1 | tail -1

echo ""

# Start all 3 brokers in background
echo ">>> Starting broker-0 (port 9092)..."
$KAFKA_HOME/bin/kafka-server-start.sh -daemon $KAFKA_HOME/config/broker-0.properties
sleep 2

echo ">>> Starting broker-1 (port 9093)..."
$KAFKA_HOME/bin/kafka-server-start.sh -daemon $KAFKA_HOME/config/broker-1.properties
sleep 2

echo ">>> Starting broker-2 (port 9094)..."
$KAFKA_HOME/bin/kafka-server-start.sh -daemon $KAFKA_HOME/config/broker-2.properties
sleep 3

echo ""
echo "=== Cluster started! ==="
echo "Brokers: localhost:9092, localhost:9093, localhost:9094"
echo ""
echo "Verify with:"
echo "  $KAFKA_HOME/bin/kafka-metadata.sh --snapshot $KAFKA_HOME/data/broker-0/__cluster_metadata-0/00000000000000000000.log --cluster-id $CLUSTER_ID"
echo ""
echo "Create a topic:"
echo "  $KAFKA_HOME/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic order-events --partitions 3 --replication-factor 3"
echo ""
echo "Stop with:"
echo "  $KAFKA_HOME/stop-cluster.sh"
