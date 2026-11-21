package com.chubb.claims.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.Deserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaConsumerFactoryCustomizer;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Kafka setup: the claim events topic (property claims.events.topic) and JSON (de)serialisation of {@link ClaimEvent} using the application's
 * ObjectMapper (so timestamps are ISO-8601 strings, same as the REST API). Keys are plain strings (Boot default).
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic claimEventsTopic(@Value("${claims.events.topic}") String topic) {
        // 3 partitions; the key (claimNumber) keeps all events of one claim on one partition, in order.
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    @Bean
    public DefaultKafkaProducerFactoryCustomizer jsonValueSerializer(ObjectMapper objectMapper) {
        return factory -> useJsonSerializer(factory, objectMapper);
    }

    @Bean
    public DefaultKafkaConsumerFactoryCustomizer jsonValueDeserializer(ObjectMapper objectMapper) {
        return factory -> useJsonDeserializer(factory, objectMapper);
    }

    @SuppressWarnings("unchecked")
    private static void useJsonSerializer(DefaultKafkaProducerFactory<?, ?> factory, ObjectMapper objectMapper) {
        JsonSerializer<Object> serializer = new JsonSerializer<>(objectMapper.copy());
        serializer.setAddTypeInfo(false); // plain JSON, no Java class names in headers
        ((DefaultKafkaProducerFactory<Object, Object>) factory).setValueSerializer(serializer);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void useJsonDeserializer(DefaultKafkaConsumerFactory<?, ?> factory, ObjectMapper objectMapper) {
        JsonDeserializer<ClaimEvent> json = new JsonDeserializer<>(ClaimEvent.class, objectMapper.copy(), false);
        // A message that can't be parsed is logged and skipped by the error handler instead of blocking the partition.
        Deserializer deserializer = new ErrorHandlingDeserializer<>(json);
        ((DefaultKafkaConsumerFactory<Object, Object>) factory).setValueDeserializer(deserializer);
    }
}
