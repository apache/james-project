/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.transport.mailets.remote.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import jakarta.mail.MessagingException;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

public class DelaysAndMaxRetryTest {

    @Test
    void fromShouldParseSingleDelay() throws Exception {
        DelaysAndMaxRetry actual = DelaysAndMaxRetry.from(1, "1s");

        DelaysAndMaxRetry expected = new DelaysAndMaxRetry(1, ImmutableList.of(new Delay(1, Duration.ofSeconds(1))));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void fromShouldParseTwoDelays() throws Exception {
        DelaysAndMaxRetry actual = DelaysAndMaxRetry.from(2, "1s,2s");

        DelaysAndMaxRetry expected = new DelaysAndMaxRetry(2, ImmutableList.of(new Delay(1, Duration.ofSeconds(1)), new Delay(1, Duration.ofSeconds(2))));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void fromShouldAdaptMaxRetriesWhenUnderAttempts() throws Exception {
        DelaysAndMaxRetry actual = DelaysAndMaxRetry.from(1, "1s,2*2s");

        DelaysAndMaxRetry expected = new DelaysAndMaxRetry(3, ImmutableList.of(new Delay(1, Duration.ofSeconds(1)), new Delay(2, Duration.ofSeconds(2))));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void fromShouldAdaptDelaysWhenUnderMaxRetries() throws Exception {
        DelaysAndMaxRetry actual = DelaysAndMaxRetry.from(4, "1s,2*2s");

        DelaysAndMaxRetry expected = new DelaysAndMaxRetry(4, ImmutableList.of(new Delay(1, Duration.ofSeconds(1)), new Delay(3, Duration.ofSeconds(2))));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void fromShouldHandleNullValuesForDelayAsString() throws Exception {
        DelaysAndMaxRetry actual = DelaysAndMaxRetry.from(1, null);

        DelaysAndMaxRetry expected = new DelaysAndMaxRetry(1, ImmutableList.of(new Delay(Delay.DEFAULT_ATTEMPTS, Delay.DEFAULT_DELAY_TIME)));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void fromShouldIgnoreEmptyDelay() throws Exception {
        DelaysAndMaxRetry actual = DelaysAndMaxRetry.from(1, "1s,,2s");

        DelaysAndMaxRetry expected = new DelaysAndMaxRetry(2, ImmutableList.of(new Delay(1, Duration.ofSeconds(1)), new Delay(1, Duration.ofSeconds(2))));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void fromShouldHandleParsingFailures() throws Exception {
        DelaysAndMaxRetry actual = DelaysAndMaxRetry.from(3, "1s,invalid,2s");

        DelaysAndMaxRetry expected = new DelaysAndMaxRetry(3, ImmutableList.of(new Delay(3, Duration.ofSeconds(1))));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void fromShouldHandleEmptyStringWithZeroMaxRetries() throws Exception {
        DelaysAndMaxRetry actual = DelaysAndMaxRetry.from(0, "");

        DelaysAndMaxRetry expected = new DelaysAndMaxRetry(0, ImmutableList.<Delay>of());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void fromShouldThrowOnEmptyStringWithNonZeroMaxRetry() {
        assertThatThrownBy(() -> DelaysAndMaxRetry.from(2, ""))
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void getExpandedDelaysShouldReturnEmptyWhenNoDelay() throws Exception {
        DelaysAndMaxRetry testee = DelaysAndMaxRetry.from(0, "");

        assertThat(testee.getExpandedDelays()).isEmpty();
    }

    @Test
    void getExpandedDelaysShouldExpandSingleDelays() throws Exception {
        DelaysAndMaxRetry testee = DelaysAndMaxRetry.from(3, "1*1S,1*2S,1*5S");

        assertThat(testee.getExpandedDelays()).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(5));
    }

    @Test
    void getExpandedDelaysShouldExpandMultipleDelays() throws Exception {
        DelaysAndMaxRetry testee = DelaysAndMaxRetry.from(3, "1*1S,2*2S,2*5S");

        assertThat(testee.getExpandedDelays())
            .containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(5));
    }

    @Test
    void getExpandedDelaysShouldExpandMultipleDelaysWithSpaces() throws Exception {
        DelaysAndMaxRetry testee = DelaysAndMaxRetry.from(3, "1 * 1 S, 2 * 2 S , 2 * 5 S");

        assertThat(testee.getExpandedDelays())
            .containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(5));
    }
}
