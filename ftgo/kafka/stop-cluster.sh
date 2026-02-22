#!/bin/bash
# ============================================================
# Stop 3-broker Kafka cluster
# ============================================================

KAFKA_HOME="$(cd "$(dirname "$0")" && pwd)"

echo ">>> Stopping broker-0..."
$KAFKA_HOME/bin/kafka-server-stop.sh 2>/dev/null

echo ">>> Stopping broker-1..."
$KAFKA_HOME/bin/kafka-server-stop.sh 2>/dev/null

echo ">>> Stopping broker-2..."
$KAFKA_HOME/bin/kafka-server-stop.sh 2>/dev/null

# Give processes time to shut down
sleep 3

# Force kill any remaining Kafka processes
PIDS=$(ps aux | grep 'kafka.Kafka' | grep -v grep | awk '{print $2}')
if [ -n "$PIDS" ]; then
    echo ">>> Force killing remaining Kafka processes: $PIDS"
    echo "$PIDS" | xargs kill -9 2>/dev/null
fi

echo "=== Cluster stopped ==="
