package org.apache.james.queue.api;

import static org.junit.jupiter.api.Assertions.*;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class MailQueueFactoryTest {

    @Test
    void prefetchCountShouldNotBeNegative() {
        Assertions.assertThatThrownBy(() -> new MailQueueFactory.PrefetchCount(-12))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void prefetchCountCouldBeZero() {
        Assertions.assertThatCode(() -> new MailQueueFactory.PrefetchCount(0)).doesNotThrowAnyException();
    }

    @Test
    void prefetchCountCouldBePositive() {
        Assertions.assertThatCode(() -> new MailQueueFactory.PrefetchCount(12)).doesNotThrowAnyException();
    }

}