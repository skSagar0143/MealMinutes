package com.mealminutes.platform.outbox;

import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Bounded retry and dead-letter routing for records that repeatedly fail
 * before the listener can durably accept them. Keep the consumer feature flag
 * off until the DLT topic and tenant infrastructure have been provisioned.
 */
@Configuration
@ConditionalOnExpression("${mealminutes.tenant-provisioning.enabled:false} and ${mealminutes.consumer.enabled:false}")
public class KafkaConsumerRecoveryConfiguration {

    @Bean
    CommonErrorHandler kafkaConsumerErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${mealminutes.consumer.retry-interval-ms:1000}") long retryIntervalMs,
            @Value("${mealminutes.consumer.retry-attempts:3}") long retryAttempts,
            @Value("${mealminutes.consumer.dead-letter-topic-suffix:.DLT}") String deadLetterTopicSuffix) {
        if (retryIntervalMs < 1) {
            throw new IllegalArgumentException("consumer retry interval must be positive");
        }
        if (retryAttempts < 0 || retryAttempts > 10) {
            throw new IllegalArgumentException("consumer retry attempts must be between 0 and 10");
        }
        if (deadLetterTopicSuffix == null || deadLetterTopicSuffix.isBlank()) {
            throw new IllegalArgumentException("consumer dead-letter topic suffix is required");
        }

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, failure) -> new TopicPartition(
                        record.topic() + deadLetterTopicSuffix, record.partition()));
        recoverer.setFailIfSendResultIsError(true);

        DefaultErrorHandler handler = new DefaultErrorHandler(
                recoverer, new FixedBackOff(retryIntervalMs, retryAttempts));
        handler.setRetryListeners((record, ex, deliveryAttempt) -> {
            // Avoid logging payloads: events may contain customer/order information.
        });
        return handler;
    }
}
