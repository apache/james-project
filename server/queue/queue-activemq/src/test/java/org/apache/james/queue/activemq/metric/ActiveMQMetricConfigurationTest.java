package org.apache.james.queue.activemq.metric;

import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.configuration2.BaseConfiguration;
import org.junit.jupiter.api.Test;

public class ActiveMQMetricConfigurationTest {

    @Test
    void shouldUseDefaultForEmptyConfiguration() {
        assertThat(ActiveMQMetricConfiguration.from(new BaseConfiguration()))
            .isNotNull();
    }

    @Test
    void shouldNotFailForValidConfiguration() {
        assertThat(getSampleConfig(1,10,4,3))
            .isNotNull();
    }

    @Test
    void shouldThrowWhenStartDelayIsLessThanMinimal() {
        assertThatThrownBy(() -> getSampleConfig(0,10,3,3))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenIntervalIsLessThanMinimal() {
        assertThatThrownBy(() -> getSampleConfig(1,1,3,3))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenTimeoutIsLessThanMinimal() {
        assertThatThrownBy(() -> getSampleConfig(1,10,1,3))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenAqmpTimeoutIsLessThanMinimal() {
        assertThatThrownBy(() -> getSampleConfig(1,10,3,0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenIntervalIsLessThanTimeout() {
        assertThatThrownBy(() -> getSampleConfig(1,5,10,2))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenTimeoutIsLessThanAqmpTimeout() {
        assertThatThrownBy(() -> getSampleConfig(1,10,3,5))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenIntervalIsLessThanAqmpTimeout() {
        assertThatThrownBy(() -> getSampleConfig(1,5,10,9))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private ActiveMQMetricConfiguration getSampleConfig(int startDelaySec, int intervalSec, int timeoutSec, int aqmpTimeoutSec) {
        return new ActiveMQMetricConfiguration(true,
            Duration.ofSeconds(startDelaySec), Duration.ofSeconds(intervalSec),
            Duration.ofSeconds(timeoutSec), Duration.ofSeconds(aqmpTimeoutSec));
    }

}
