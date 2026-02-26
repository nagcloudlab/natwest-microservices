package com.demo.query;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

@Component
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);
    private static final String TOPIC = "greeting-events";

    private final ReadModelStore readModelStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    public EventConsumer(ReadModelStore readModelStore) {
        this.readModelStore = readModelStore;
    }

    @KafkaListener(topics = TOPIC, groupId = "query-service")
    public void onEvent(String payload) {
        try {
            Map<String, Object> event = objectMapper.readValue(payload,
                    new TypeReference<Map<String, Object>>() {});
            readModelStore.apply(event);
            log.info("Applied event: sequence={}, name={}", event.get("sequence"), event.get("name"));
        } catch (Exception e) {
            log.error("Failed to process event: {}", e.getMessage());
        }
    }

    public int rebuild() {
        log.info("Starting rebuild — clearing all read models");
        readModelStore.clear();

        String groupId = "rebuild-" + UUID.randomUUID();
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        int eventsReplayed = 0;

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(TOPIC));

            // Force assignment by polling once
            consumer.poll(Duration.ofSeconds(5));

            // Seek to beginning of all assigned partitions
            Set<TopicPartition> partitions = consumer.assignment();
            consumer.seekToBeginning(partitions);

            // Replay all events
            boolean hasMore = true;
            while (hasMore) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
                if (records.isEmpty()) {
                    hasMore = false;
                } else {
                    for (ConsumerRecord<String, String> record : records) {
                        Map<String, Object> event = objectMapper.readValue(record.value(),
                                new TypeReference<Map<String, Object>>() {});
                        readModelStore.apply(event);
                        eventsReplayed++;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Rebuild failed: {}", e.getMessage(), e);
        }

        log.info("Rebuild complete — replayed {} events", eventsReplayed);
        return eventsReplayed;
    }
}
