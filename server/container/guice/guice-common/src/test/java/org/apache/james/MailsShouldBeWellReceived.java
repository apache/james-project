/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.james;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.james.core.Domain;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.IMAPMessageReader;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SpoolerProbe;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.google.common.io.Resources;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

interface MailsShouldBeWellReceived {

    String JAMES_SERVER_HOST = "127.0.0.1";
    String DOMAIN = "apache.org";
    String JAMES_USER = "james-user@" + DOMAIN;
    String PASSWORD = "secret";
    ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(Duration.ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(Duration.ONE_HUNDRED_MILLISECONDS)
        .await();

    ConditionFactory CALMLY_AWAIT_FIVE_MINUTE = CALMLY_AWAIT.timeout(Duration.FIVE_MINUTES);

    @Test
    default void mailsShouldBeWellReceived(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(JAMES_USER, PASSWORD);

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox("#private", JAMES_USER, DefaultMailboxes.INBOX);

        Port smtpPort = server.getProbe(SmtpGuiceProbe.class).getSmtpPort();
        String message = Resources.toString(Resources.getResource("eml/htmlMail.eml"), StandardCharsets.UTF_8);

        try (SMTPMessageSender sender = new SMTPMessageSender(Domain.LOCALHOST.asString())) {
            Mono.fromRunnable(
                Throwing.runnable(() -> {
                    sender.connect(JAMES_SERVER_HOST, smtpPort);
                    sendUniqueMessage(sender, message);
                }))
                .subscribeOn(Schedulers.elastic())
                .block();
        }

        CALMLY_AWAIT.until(() -> server.getProbe(SpoolerProbe.class).processingFinished());

        try (IMAPMessageReader reader = new IMAPMessageReader()) {
            reader.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(JAMES_USER, PASSWORD)
                .select(IMAPMessageReader.INBOX)
                .awaitMessageCount(CALMLY_AWAIT, 1);
        }

    }

    @Test
    default void oneHundredMailsShouldBeWellReceived(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(JAMES_USER, PASSWORD);

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox("#private", JAMES_USER, DefaultMailboxes.INBOX);

        int messageCount = 100;

        Port smtpPort = server.getProbe(SmtpGuiceProbe.class).getSmtpPort();
        String message = Resources.toString(Resources.getResource("eml/htmlMail.eml"), StandardCharsets.UTF_8);

        try (SMTPMessageSender sender = new SMTPMessageSender(Domain.LOCALHOST.asString())) {
            Mono.fromRunnable(
                Throwing.runnable(() -> {
                    sender.connect(JAMES_SERVER_HOST, smtpPort);
                    sendUniqueMessage(sender, message);
            }))
                .repeat(messageCount - 1)
                .subscribeOn(Schedulers.elastic())
                .blockLast();
        }

        CALMLY_AWAIT_FIVE_MINUTE.until(() -> server.getProbe(SpoolerProbe.class).processingFinished());

        try (IMAPMessageReader reader = new IMAPMessageReader()) {
            reader.connect(JAMES_SERVER_HOST, server.getProbe(ImapGuiceProbe.class).getImapPort())
                .login(JAMES_USER, PASSWORD)
                .select(IMAPMessageReader.INBOX)
                .awaitMessageCount(CALMLY_AWAIT, messageCount);
        }
    }

    default void sendUniqueMessage(SMTPMessageSender sender, String message) throws IOException {
        String uniqueMessage = message.replace("banana", "UUID " + UUID.randomUUID().toString());
        sender.sendMessageWithHeaders("bob@apache.org", JAMES_USER, uniqueMessage);
    }
}
