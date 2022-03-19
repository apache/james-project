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

package org.apache.james.transport.matchers;

import static org.apache.james.transport.matchers.AtMost.AT_MOST_EXECUTIONS;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collection;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;

class AtMostTest {
    private static final String CONDITION = "2";

    private AtMost matcher;

    private Mail createMail() throws MessagingException {
        return FakeMail.builder()
            .name("test-message")
            .recipient(RECIPIENT1)
            .build();
    }

    @BeforeEach
    void setup() throws MessagingException {
        this.matcher = new AtMost();
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
            .matcherName("AtMost")
            .condition(CONDITION)
            .build();
        matcher.init(matcherConfig);
    }

    @Nested
    class InvalidConditionConfigurationTest {
        @Test
        void shouldThrowWhenMatchersConfigWithoutConditionValue() {
            assertThatThrownBy(() -> new AtMost().init(FakeMatcherConfig.builder()
                    .matcherName("NoValueMatcher")
                    .condition("randomName:")
                    .build()))
                .isInstanceOf(MessagingException.class);
        }

        @Test
        void shouldThrowWhenMatchersConfigWithInvalidConditionValue() {
            assertThatThrownBy(() -> new AtMost().init(FakeMatcherConfig.builder()
                    .matcherName("NoValueMatcher")
                    .condition("value")
                    .build()))
                .isInstanceOf(MessagingException.class);
        }

        @Test
        void shouldThrowWhenMatchersConfigWithoutConditionName() {
            assertThatThrownBy(() -> new AtMost().init(FakeMatcherConfig.builder()
                    .matcherName("NoValueMatcher")
                    .condition(":3")
                    .build()))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldThrowWhenMatchersConfigNameAsSpace() {
            assertThatThrownBy(() -> new AtMost().init(FakeMatcherConfig.builder()
                    .matcherName("NoValueMatcher")
                    .condition("  :  ")
                    .build()))
                .isInstanceOf(MessagingException.class);
        }


        @Test
        void shouldThrowWithEmptyCondition() {
            FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
                .matcherName("AtMost")
                .build();

            assertThatThrownBy(() -> matcher.init(matcherConfig))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldThrowWithNegativeCondition() {
            FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
                .matcherName("AtMost")
                .condition("-1")
                .build();

            assertThatThrownBy(() -> matcher.init(matcherConfig))
                .isInstanceOf(MessagingException.class);
        }

        @Test
        void shouldThrowWithConditionToZero() {
            FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
                .matcherName("AtMost")
                .condition("0")
                .build();

            assertThatThrownBy(() -> matcher.init(matcherConfig))
                .isInstanceOf(MessagingException.class);
        }
    }

    @Nested
    class MultipleMatchersConfigurationTest {
        private AtMost atMost2;
        private AtMost atMost3;

        @BeforeEach
        void setup() throws MessagingException {
            this.atMost2 = new AtMost();
            atMost2.init(
                FakeMatcherConfig.builder()
                    .matcherName("AtMost")
                    .condition("AtMost2:2")
                    .build());

            this.atMost3 = new AtMost();
            atMost3.init(
                FakeMatcherConfig.builder()
                    .matcherName("AtMost")
                    .condition("AtMost3:2")
                    .build());
        }

        @Test
        void matchersShouldStopWhenAMatcherReachedLimit() throws MessagingException {
            Mail mail1 = createMail();

            SoftAssertions.assertSoftly(Throwing.consumer(
                softly -> {
                    softly.assertThat(atMost2.match(mail1)).containsOnly(RECIPIENT1);
                    softly.assertThat(atMost2.match(mail1)).containsOnly(RECIPIENT1);
                    softly.assertThat(atMost2.match(mail1)).isEmpty();
                    softly.assertThat(atMost3.match(mail1)).containsOnly(RECIPIENT1);
                    softly.assertThat(atMost3.match(mail1)).containsOnly(RECIPIENT1);
                    softly.assertThat(atMost3.match(mail1)).isEmpty();
                }));
        }
    }

    @Nested
    class SingleMatcherConfigurationTest {
        @Test
        void shouldMatchWhenAttributeNotSet() throws MessagingException {
            Mail mail = createMail();

            Collection<MailAddress> actual = matcher.match(mail);

            assertThat(actual).containsOnly(RECIPIENT1);
        }

        @Test
        void shouldMatchWhenNoRetries() throws MessagingException {
            Mail mail = createMail();
            mail.setAttribute(new Attribute(AT_MOST_EXECUTIONS, AttributeValue.of(0)));

            Collection<MailAddress> actual = matcher.match(mail);

            assertThat(actual).containsOnly(RECIPIENT1);
        }

        @Test
        void shouldNotMatchWhenOverAtMost() throws MessagingException {
            Mail mail = createMail();
            mail.setAttribute(new Attribute(AT_MOST_EXECUTIONS, AttributeValue.of(3)));

            Collection<MailAddress> actual = matcher.match(mail);

            assertThat(actual).isEmpty();
        }

        @Test
        void shouldNotMatchWhenEqualToAtMost() throws MessagingException {
            Mail mail = createMail();
            mail.setAttribute(new Attribute(AT_MOST_EXECUTIONS, AttributeValue.of(2)));

            Collection<MailAddress> actual = matcher.match(mail);

            assertThat(actual).isEmpty();
        }

        @Test
        void shouldMatchUntilOverAtMost() throws MessagingException {
            Mail mail = createMail();

            SoftAssertions.assertSoftly(Throwing.consumer(softly -> {
                softly.assertThat(matcher.match(mail)).describedAs("First execution").contains(RECIPIENT1);
                softly.assertThat(matcher.match(mail)).describedAs("Second execution").contains(RECIPIENT1);
                softly.assertThat(matcher.match(mail)).describedAs("Third execution").isEmpty();
            }));
        }
    }
}
