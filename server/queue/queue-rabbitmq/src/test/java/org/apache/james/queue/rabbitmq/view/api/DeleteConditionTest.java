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

package org.apache.james.queue.rabbitmq.view.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.core.MailAddress;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class DeleteConditionTest {
    private static final String ADDRESS = "any@toto.com";
    private static final String ADDRESS_2 = "any2@toto.com";
    private static final String NAME = "name";
    private static final String VALUE = "value";

    @Nested
    class AllTest {
        @Test
        void allShouldReturnTrue() throws Exception {
            assertThat(
                DeleteCondition.all()
                    .shouldBeDeleted(FakeMail.builder().name("name").build()))
                .isTrue();
        }

        @Test
        void allShouldThrowWhenNull() {
            assertThatThrownBy(() ->
                DeleteCondition.all()
                    .shouldBeDeleted(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldMatchBeanContract() {
            EqualsVerifier.forClass(DeleteCondition.All.class).verify();
        }
    }

    @Nested
    class WithSenderTest {
        @Test
        void withSenderShouldThrowOnNullCondition() {
            assertThatThrownBy(() ->
                DeleteCondition.withSender(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void withSenderShouldThrowOnNullMail() {
            assertThatThrownBy(() ->
                DeleteCondition.withSender(ADDRESS)
                    .shouldBeDeleted(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void withSenderShouldReturnTrueWhenSameAddress() throws Exception {
            assertThat(
                DeleteCondition.withSender(ADDRESS)
                    .shouldBeDeleted(FakeMail.builder()
                        .name("name")
                        .sender(ADDRESS)
                        .build()))
                .isTrue();
        }

        @Test
        void withSenderShouldReturnFalseWhenDifferentAddress() throws Exception {
            assertThat(
                DeleteCondition.withSender(ADDRESS)
                    .shouldBeDeleted(FakeMail.builder()
                        .name("name")
                        .sender(ADDRESS_2)
                        .recipient(ADDRESS)
                        .build()))
                .isFalse();
        }

        @Test
        void withSenderShouldNotThrowOnNullSender() throws Exception {
            assertThat(
                DeleteCondition.withSender(ADDRESS)
                    .shouldBeDeleted(FakeMail.builder()
                        .name("name")
                        .sender(MailAddress.nullSender())
                        .build()))
                .isFalse();
        }

        @Test
        void withSenderShouldAllowNullSenderMatchingNullSender() throws Exception {
            assertThat(
                DeleteCondition.withSender(MailAddress.NULL_SENDER_AS_STRING)
                    .shouldBeDeleted(FakeMail.builder()
                        .name("name")
                        .sender(MailAddress.nullSender())
                        .build()))
                .isTrue();
        }

        @Test
        void shouldMatchBeanContract() {
            EqualsVerifier.forClass(DeleteCondition.WithSender.class).verify();
        }
    }

    @Nested
    class WithNameTest {
        @Test
        void withNameShouldThrowOnNullCondition() {
            assertThatThrownBy(() ->
                DeleteCondition.withName(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void withNameShouldThrowOnNullMail() {
            assertThatThrownBy(() ->
                DeleteCondition.withName(NAME)
                    .shouldBeDeleted(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void withNameShouldReturnTrueWhenSameName() throws Exception {
            assertThat(
                DeleteCondition.withName(NAME)
                    .shouldBeDeleted(FakeMail.builder()
                        .name(NAME)
                        .build()))
                .isTrue();
        }

        @Test
        void withSenderShouldReturnFalseWhenDifferentAddress() throws Exception {
            assertThat(
                DeleteCondition.withName(NAME)
                    .shouldBeDeleted(FakeMail.builder()
                        .name("other")
                        .build()))
                .isFalse();
        }

        @Test
        void shouldMatchBeanContract() {
            EqualsVerifier.forClass(DeleteCondition.WithName.class).verify();
        }
    }

    @Nested
    class WithRecipientTest {
        @Test
        void withRecipientShouldThrowOnNullCondition() {
            assertThatThrownBy(() ->
                DeleteCondition.withRecipient(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void withRecipientShouldThrowOnNullMail() {
            assertThatThrownBy(() ->
                DeleteCondition.withRecipient(ADDRESS)
                    .shouldBeDeleted(null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void withRecipientShouldReturnTrueWhenSameAddress() throws Exception {
            assertThat(
                DeleteCondition.withRecipient(ADDRESS)
                    .shouldBeDeleted(FakeMail.builder()
                        .name("name")
                        .recipient(ADDRESS)
                        .build()))
                .isTrue();
        }

        @Test
        void withRecipientShouldReturnTrueWhenAtListOneMatches() throws Exception {
            assertThat(
                DeleteCondition.withRecipient(ADDRESS)
                    .shouldBeDeleted(FakeMail.builder()
                        .name("name")
                        .recipients(ADDRESS, ADDRESS_2)
                        .build()))
                .isTrue();
        }

        @Test
        void withRecipientShouldReturnFalseWhenDifferentAddress() throws Exception {
            assertThat(
                DeleteCondition.withRecipient(ADDRESS)
                    .shouldBeDeleted(FakeMail.builder()
                        .name("name")
                        .sender(ADDRESS)
                        .recipient(ADDRESS_2)
                        .build()))
                .isFalse();
        }

        @Test
        void withRecipientShouldReturnFalseWhenNoRecipient() throws Exception {
            assertThat(
                DeleteCondition.withRecipient(ADDRESS)
                    .shouldBeDeleted(FakeMail.builder()
                        .name("name")
                        .build()))
                .isFalse();
        }

        @Test
        void shouldMatchBeanContract() {
            EqualsVerifier.forClass(DeleteCondition.WithRecipient.class).verify();
        }
    }

    @Nested
    class ConvertTest {
        @Test
        void senderShouldBeConvertedToWithSender() {
            assertThat(DeleteCondition.from(ManageableMailQueue.Type.Sender, VALUE))
                .isEqualTo(DeleteCondition.withSender(VALUE));
        }

        @Test
        void recipientShouldBeConvertedToWithRecipient() {
            assertThat(DeleteCondition.from(ManageableMailQueue.Type.Recipient, VALUE))
                .isEqualTo(DeleteCondition.withRecipient(VALUE));
        }

        @Test
        void nameShouldBeConvertedToWithName() {
            assertThat(DeleteCondition.from(ManageableMailQueue.Type.Name, VALUE))
                .isEqualTo(DeleteCondition.withName(VALUE));
        }
    }
}