package com.neueda.leap.config;

import com.neueda.leap.messaging.EventEnvelope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.clients.producer.ProducerConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for EventEnvelope and String message serialization.
 * Configures KafkaTemplates for publishing EventEnvelope and String messages.
 * Bootstrap servers are injected from application.yml to support different environments.
 */
@Configuration
public class KafkaConfig {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    /**
     * KafkaTemplate bean for publishing EventEnvelope messages.
     * Uses String keys (accountId) and EventEnvelope values with JSON serialization.
     */
    @Bean
    public KafkaTemplate<String, EventEnvelope> kafkaTemplate(
            ProducerFactory<String, EventEnvelope> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
    
    /**
     * ProducerFactory for EventEnvelope messages.
     */
    @Bean
    public ProducerFactory<String, EventEnvelope> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }
    
    /**
     * KafkaTemplate bean for publishing String messages (for orders and executions).
     * Uses String keys and String values with JSON serialization.
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplateString(
            ProducerFactory<String, String> producerFactoryString) {
        return new KafkaTemplate<>(producerFactoryString);
    }
    
    /**
     * ProducerFactory for String messages.
     */
    @Bean
    public ProducerFactory<String, String> producerFactoryString() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }
}
