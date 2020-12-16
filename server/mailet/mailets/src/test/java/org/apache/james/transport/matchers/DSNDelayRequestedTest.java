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

import static org.apache.mailet.DsnParameters.Notify.DELAY;
import static org.apache.mailet.DsnParameters.Notify.FAILURE;
import static org.apache.mailet.DsnParameters.Notify.NEVER;
import static org.apache.mailet.DsnParameters.Notify.SUCCESS;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;

import org.apache.james.server.core.MailImpl;
import org.apache.mailet.DsnParameters;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DSNDelayRequestedTest {
    @Nested
    class Default {
        DSNDelayRequested testee;

        @BeforeEach
        void setUp() throws Exception {
            testee = new DSNDelayRequested();
            testee.init(FakeMatcherConfig.builder()
                .matcherName(testee.getMatcherName())
                .build());
        }

        @Test
        void shouldReturnEmptyWhenNoDsnParameters() {
            assertThat(
                testee.match(MailImpl.builder()
                    .name("mail")
                    .addRecipient(RECIPIENT1)
                    .build()))
                .isEmpty();
        }

        @Test
        void shouldReturnEmptyWhenNoDsnRcptParameters() {
            MailImpl mail = MailImpl.builder()
                .name("mail")
                .addRecipient(RECIPIENT1)
                .build();
            mail.setDsnParameters(DsnParameters.builder()
                .envId(DsnParameters.EnvId.of("39"))
                .build().get());

            assertThat(testee.match(mail))
                .isEmpty();
        }

        @Test
        void shouldReturnEmptyWhenNoDsnNotifyParameters() {
            MailImpl mail = MailImpl.builder()
                .name("mail")
                .addRecipient(RECIPIENT1)
                .build();
            mail.setDsnParameters(DsnParameters.builder()
                .addRcptParameter(RECIPIENT1, DsnParameters.RecipientDsnParameters.of(RECIPIENT1))
                .build().get());

            assertThat(testee.match(mail))
                .isEmpty();
        }

        @Test
        void shouldReturnEmptyWhenNeverNotifyParameter() {
            MailImpl mail = MailImpl.builder()
                .name("mail")
                .addRecipient(RECIPIENT1)
                .build();
            mail.setDsnParameters(DsnParameters.builder()
                .addRcptParameter(RECIPIENT1, DsnParameters.RecipientDsnParameters.of(EnumSet.of(NEVER)))
                .build().get());

            assertThat(testee.match(mail))
                .isEmpty();
        }

        @Test
        void shouldReturnEmptyWhenNotDelayNotifyParameter() {
            MailImpl mail = MailImpl.builder()
                .name("mail")
                .addRecipient(RECIPIENT1)
                .build();
            mail.setDsnParameters(DsnParameters.builder()
                .addRcptParameter(RECIPIENT1, DsnParameters.RecipientDsnParameters.of(EnumSet.of(SUCCESS, FAILURE)))
                .build().get());

            assertThat(testee.match(mail))
                .isEmpty();
        }

        @Test
        void shouldReturnCollectionWhenDelayNotifyParameter() {
            MailImpl mail = MailImpl.builder()
                .name("mail")
                .addRecipient(RECIPIENT1)
                .build();
            mail.setDsnParameters(DsnParameters.builder()
                .addRcptParameter(RECIPIENT1, DsnParameters.RecipientDsnParameters.of(EnumSet.of(DELAY)))
                .build().get());

            assertThat(testee.match(mail))
                .containsOnly(RECIPIENT1);
        }
    }

    @Nested
    class ShouldMatchByDefault {
        DSNDelayRequested testee;

        @BeforeEach
        void setUp() throws Exception {
            testee = new DSNDelayRequested();
            testee.init(FakeMatcherConfig.builder()
                .matcherName(testee.getMatcherName())
                .condition("shouldMatchByDefault")
                .build());
        }

        @Test
        void shouldReturnCollectionWhenNoDsnParameters() {
            assertThat(
                testee.match(MailImpl.builder()
                    .name("mail")
                    .addRecipient(RECIPIENT1)
                    .build()))
                .containsOnly(RECIPIENT1);
        }

        @Test
        void shouldReturnCollectionWhenNoDsnRcptParameters() {
            MailImpl mail = MailImpl.builder()
                .name("mail")
                .addRecipient(RECIPIENT1)
                .build();
            mail.setDsnParameters(DsnParameters.builder()
                .envId(DsnParameters.EnvId.of("39"))
                .build().get());

            assertThat(testee.match(mail))
                .containsOnly(RECIPIENT1);
        }

        @Test
        void shouldReturnCollectionWhenNoDsnNotifyParameters() {
            MailImpl mail = MailImpl.builder()
                .name("mail")
                .addRecipient(RECIPIENT1)
                .build();
            mail.setDsnParameters(DsnParameters.builder()
                .addRcptParameter(RECIPIENT1, DsnParameters.RecipientDsnParameters.of(RECIPIENT1))
                .build().get());

            assertThat(testee.match(mail))
                .containsOnly(RECIPIENT1);
        }

        @Test
        void shouldReturnEmptyWhenNeverNotifyParameter() {
            MailImpl mail = MailImpl.builder()
                .name("mail")
                .addRecipient(RECIPIENT1)
                .build();
            mail.setDsnParameters(DsnParameters.builder()
                .addRcptParameter(RECIPIENT1, DsnParameters.RecipientDsnParameters.of(EnumSet.of(NEVER)))
                .build().get());

            assertThat(testee.match(mail))
                .isEmpty();
        }

        @Test
        void shouldReturnEmptyWhenNotDelayNotifyParameter() {
            MailImpl mail = MailImpl.builder()
                .name("mail")
                .addRecipient(RECIPIENT1)
                .build();
            mail.setDsnParameters(DsnParameters.builder()
                .addRcptParameter(RECIPIENT1, DsnParameters.RecipientDsnParameters.of(EnumSet.of(SUCCESS, FAILURE)))
                .build().get());

            assertThat(testee.match(mail))
                .isEmpty();
        }

        @Test
        void shouldReturnCollectionWhenDelayNotifyParameter() {
            MailImpl mail = MailImpl.builder()
                .name("mail")
                .addRecipient(RECIPIENT1)
                .build();
            mail.setDsnParameters(DsnParameters.builder()
                .addRcptParameter(RECIPIENT1, DsnParameters.RecipientDsnParameters.of(EnumSet.of(DELAY)))
                .build().get());

            assertThat(testee.match(mail))
                .containsOnly(RECIPIENT1);
        }
    }

    @Nested
    class Configuration {
        @Test
        void shouldThrowOnInvalidValue() {
            assertThatThrownBy(() -> new DSNDelayRequested()
                .init(FakeMatcherConfig.builder()
                    .matcherName("any")
                    .condition("bad")
                    .build()))
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void shouldAcceptMatchByDefault() {
            assertThatCode(() -> new DSNDelayRequested()
                .init(FakeMatcherConfig.builder()
                    .matcherName("any")
                    .condition("shouldMatchByDefault")
                    .build()))
                .doesNotThrowAnyException();
        }

        @Test
        void shouldAcceptNoCondition() {
            assertThatCode(() -> new DSNDelayRequested()
                .init(FakeMatcherConfig.builder()
                    .matcherName("any")
                    .build()))
                .doesNotThrowAnyException();
        }
    }
}