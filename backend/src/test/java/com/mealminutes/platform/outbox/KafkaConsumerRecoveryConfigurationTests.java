package com.mealminutes.platform.outbox;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class KafkaConsumerRecoveryConfigurationTests {

    private final KafkaConsumerRecoveryConfiguration configuration = new KafkaConsumerRecoveryConfiguration();

    @Test
    void createsBoundedRetryAndDeadLetterErrorHandler() {
        CommonErrorHandler handler = configuration.kafkaConsumerErrorHandler(
                mock(KafkaTemplate.class), 1000, 3, ".DLT");

        assertThat(handler).isInstanceOf(DefaultErrorHandler.class);
    }

    @Test
    void rejectsNonPositiveRetryInterval() {
        assertThatThrownBy(() -> configuration.kafkaConsumerErrorHandler(
                mock(KafkaTemplate.class), 0, 3, ".DLT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retry interval");
    }

    @Test
    void rejectsUnboundedRetryCount() {
        assertThatThrownBy(() -> configuration.kafkaConsumerErrorHandler(
                mock(KafkaTemplate.class), 1000, 11, ".DLT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retry attempts");
    }

    @Test
    void rejectsMissingDeadLetterSuffix() {
        assertThatThrownBy(() -> configuration.kafkaConsumerErrorHandler(
                mock(KafkaTemplate.class), 1000, 3, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("suffix");
    }
}
