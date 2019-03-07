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

package org.apache.james.server.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

class EnvelopeTest {
    @Test
    void fromMime4JMessageShouldParseAllHeaders() throws Exception {
        Message message = toMime4JMessage(
            "From: bob@domain\r\n" +
            "To: alice@domain, cedric@domain\r\n" +
            "Cc: dave@domain\r\n" +
            "Bcc: edgard@domain\r\n");

        Envelope envelope = Envelope.fromMime4JMessage(message);

        assertThat(envelope).isEqualTo(
            new Envelope(MaybeSender.of(new MailAddress("bob@domain")),
                ImmutableSet.of(new MailAddress("alice@domain"),
                    new MailAddress("cedric@domain"),
                    new MailAddress("dave@domain"),
                    new MailAddress("edgard@domain"))));
    }

    @Test
    void fromMime4JMessageShouldParseFoldedHeaders() throws Exception {
        Message message = toMime4JMessage(
            "From: bob@domain\r\n" +
            "To: alice@domain,\r\n" +
            " cedric@domain\r\n" +
            "Cc: dave@domain\r\n" +
            "Bcc: edgard@domain\r\n");

        Envelope envelope = Envelope.fromMime4JMessage(message);

        assertThat(envelope).isEqualTo(
            new Envelope(MaybeSender.of(new MailAddress("bob@domain")),
                ImmutableSet.of(new MailAddress("alice@domain"),
                    new MailAddress("cedric@domain"),
                    new MailAddress("dave@domain"),
                    new MailAddress("edgard@domain"))));
    }

    @Test
    void fromMime4JMessageShouldParseMailboxAddresses() throws Exception {
        Message message = toMime4JMessage(
            "From: bob@domain\r\n" +
            "To: alice@domain, \"CEDRIC\" <cedric@domain>\r\n" +
            "Cc: dave@domain\r\n" +
            "Bcc: edgard@domain\r\n");

        Envelope envelope = Envelope.fromMime4JMessage(message);

        assertThat(envelope).isEqualTo(
            new Envelope(MaybeSender.of(new MailAddress("bob@domain")),
                ImmutableSet.of(new MailAddress("alice@domain"),
                    new MailAddress("cedric@domain"),
                    new MailAddress("dave@domain"),
                    new MailAddress("edgard@domain"))));
    }

    @Test
    void fromMime4JMessageShouldParseEncodedMailboxAddresses() throws Exception {
        Message message = toMime4JMessage(
            "From: bob@domain\r\n" +
            "To: alice@domain, =?UTF-8?B?RnLDqWTDqXJpYyBNQVJUSU4=?= <cedric@domain>\r\n" +
            "Cc: dave@domain\r\n" +
            "Bcc: edgard@domain\r\n");

        Envelope envelope = Envelope.fromMime4JMessage(message);

        assertThat(envelope).isEqualTo(
            new Envelope(MaybeSender.of(new MailAddress("bob@domain")),
                ImmutableSet.of(new MailAddress("alice@domain"),
                    new MailAddress("cedric@domain"),
                    new MailAddress("dave@domain"),
                    new MailAddress("edgard@domain"))));
    }

    @Test
    void fromMime4JMessageShouldParseMailboxAddressesWithoutName() throws Exception {
        Message message = toMime4JMessage(
            "From: bob@domain\r\n" +
            "To: alice@domain, <cedric@domain>\r\n" +
            "Cc: dave@domain\r\n" +
            "Bcc: edgard@domain\r\n");

        Envelope envelope = Envelope.fromMime4JMessage(message);

        assertThat(envelope).isEqualTo(
            new Envelope(MaybeSender.of(new MailAddress("bob@domain")),
                ImmutableSet.of(new MailAddress("alice@domain"),
                    new MailAddress("cedric@domain"),
                    new MailAddress("dave@domain"),
                    new MailAddress("edgard@domain"))));
    }

    @Test
    void fromMime4JMessageShouldNotThrowOnNullSender() throws Exception {
        Message message = toMime4JMessage(
            "From: <>\r\n" +
            "To: alice@domain, cedric@domain\r\n" +
            "Cc: dave@domain\r\n" +
            "Bcc: edgard@domain\r\n");

        Envelope envelope = Envelope.fromMime4JMessage(message);

        assertThat(envelope).isEqualTo(
            new Envelope(MaybeSender.nullSender(),
                ImmutableSet.of(new MailAddress("alice@domain"),
                    new MailAddress("cedric@domain"),
                    new MailAddress("dave@domain"),
                    new MailAddress("edgard@domain"))));
    }

    @Test
    void fromMime4JMessageShouldNotThrowOnMissingFromHeader() throws Exception {
        Message message = toMime4JMessage(
            "To: alice@domain, cedric@domain\r\n" +
            "Cc: dave@domain\r\n" +
            "Bcc: edgard@domain\r\n");

        Envelope envelope = Envelope.fromMime4JMessage(message);

        assertThat(envelope).isEqualTo(
            new Envelope(MaybeSender.nullSender(),
                ImmutableSet.of(new MailAddress("alice@domain"),
                    new MailAddress("cedric@domain"),
                    new MailAddress("dave@domain"),
                    new MailAddress("edgard@domain"))));
    }

    @Test
    void fromMime4JMessageShouldPreserveValidEnvelopeWhenIgnore() throws Exception {
        Message message = toMime4JMessage(
            "From: bob@domain\r\n" +
            "To: alice@domain, bad@bad@domain\r\n" +
            "Cc: dave@domain\r\n" +
            "Bcc: edgard@domain\r\n");

        Envelope envelope = Envelope.fromMime4JMessage(message, Envelope.ValidationPolicy.IGNORE);

        assertThat(envelope).isEqualTo(
            new Envelope(MaybeSender.of(new MailAddress("bob@domain")),
                ImmutableSet.of(new MailAddress("alice@domain"),
                    new MailAddress("dave@domain"),
                    new MailAddress("edgard@domain"))));
    }

    @Test
    void fromMime4JMessageShouldThrowWhenThrowingValidationPolicyAndInvalidAddress() throws Exception {
        Message message = toMime4JMessage(
            "From: bob@domain\r\n" +
            "To: alice@domain, bad@bad@domain\r\n" +
            "Cc: dave@domain\r\n" +
            "Bcc: edgard@domain\r\n");

        assertThatThrownBy(() -> Envelope.fromMime4JMessage(message, Envelope.ValidationPolicy.THROW))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void fromMime4JMessageShouldNotThrowOnMissingBccHeader() throws Exception {
        Message message = toMime4JMessage(
            "From: bob@domain\r\n" +
            "To: alice@domain, cedric@domain\r\n" +
            "Cc: dave@domain\r\n");

        Envelope envelope = Envelope.fromMime4JMessage(message);

        assertThat(envelope).isEqualTo(
            new Envelope(MaybeSender.of(new MailAddress("bob@domain")),
                ImmutableSet.of(new MailAddress("alice@domain"),
                    new MailAddress("cedric@domain"),
                    new MailAddress("dave@domain"))));
    }

    @Test
    void fromMime4JMessageShouldNotThrowOnMissingCcHeader() throws Exception {
        Message message = toMime4JMessage(
            "From: bob@domain\r\n" +
            "To: alice@domain, cedric@domain\r\n" +
            "Bcc: edgard@domain\r\n");

        Envelope envelope = Envelope.fromMime4JMessage(message);

        assertThat(envelope).isEqualTo(
            new Envelope(MaybeSender.of(new MailAddress("bob@domain")),
                ImmutableSet.of(new MailAddress("alice@domain"),
                    new MailAddress("cedric@domain"),
                    new MailAddress("edgard@domain"))));
    }

    @Test
    void fromMime4JMessageShouldNotThrowOnMissingToHeader() throws Exception {
        Message message = toMime4JMessage(
            "From: bob@domain\r\n" +
            "Cc: dave@domain\r\n" +
            "Bcc: edgard@domain\r\n");

        Envelope envelope = Envelope.fromMime4JMessage(message);

        assertThat(envelope).isEqualTo(
            new Envelope(MaybeSender.of(new MailAddress("bob@domain")),
                ImmutableSet.of(new MailAddress("dave@domain"),
                    new MailAddress("edgard@domain"))));
    }

    private Message toMime4JMessage(String messageAsString) throws IOException {
        DefaultMessageBuilder defaultMessageBuilder = new DefaultMessageBuilder();
        defaultMessageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
        defaultMessageBuilder.setDecodeMonitor(DecodeMonitor.SILENT);
        return defaultMessageBuilder.parseMessage(
            new ByteArrayInputStream(messageAsString.getBytes(StandardCharsets.UTF_8)));
    }
}