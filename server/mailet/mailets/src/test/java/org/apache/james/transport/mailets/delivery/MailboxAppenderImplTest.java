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

package org.apache.james.transport.mailets.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Optional;

import javax.mail.Flags;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.apache.mailet.StorageDirective;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class MailboxAppenderImplTest {

    public static final Username USER = Username.of("user");
    public static final String FOLDER = "folder";
    public static final StorageDirective STORAGE_DIRECTIVE = StorageDirective.builder()
        .targetFolder(FOLDER)
        .build();
    public static final String EMPTY_FOLDER = "";
    private static final Optional<Flags> NO_FLAGS = Optional.empty();

    private MailboxAppenderImpl testee;
    private MailboxManager mailboxManager;
    private MimeMessage mimeMessage;
    private MailboxSession session;

    @BeforeEach
    void setUp() throws Exception {
        mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("toto"))
            .build();

        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        testee = new MailboxAppenderImpl(mailboxManager);

        session = mailboxManager.createSystemSession(USER);
    }

    @Test
    void appendShouldAddMessageToDesiredMailbox() throws Exception {
        Mono.from(testee.append(mimeMessage, USER, STORAGE_DIRECTIVE)).block();

        MessageResultIterator messages = mailboxManager.getMailbox(MailboxPath.forUser(USER, FOLDER), session)
            .getMessages(MessageRange.all(), FetchGroup.FULL_CONTENT, session);

        assertThat(messages).toIterable()
            .hasSize(1);
    }

    @Test
    void appendShouldAddMessageToDesiredMailboxWhenMailboxExists() throws Exception {
        MailboxPath mailboxPath = MailboxPath.forUser(USER, FOLDER);
        mailboxManager.createMailbox(mailboxPath, session);

        Mono.from(testee.append(mimeMessage, USER, STORAGE_DIRECTIVE)).block();

        MessageResultIterator messages = mailboxManager.getMailbox(mailboxPath, session)
            .getMessages(MessageRange.all(), FetchGroup.FULL_CONTENT, session);

        assertThat(messages).toIterable()
            .hasSize(1);
    }

    @Test
    void appendShouldNotAppendToEmptyFolder() {
        assertThatThrownBy(() -> Mono.from(testee.append(mimeMessage, USER, StorageDirective.builder()
            .targetFolder(EMPTY_FOLDER)
            .build())).block())
            .isInstanceOf(MessagingException.class);
    }

    @Test
    void appendShouldRemovePathSeparatorAsFirstChar() throws Exception {
        Mono.from(testee.append(mimeMessage, USER, StorageDirective.builder()
            .targetFolder("." + FOLDER)
            .build())).block();

        MessageResultIterator messages = mailboxManager.getMailbox(MailboxPath.forUser(USER, FOLDER), session)
            .getMessages(MessageRange.all(), FetchGroup.FULL_CONTENT, session);

        assertThat(messages).toIterable()
            .hasSize(1);
    }

    @Test
    void appendShouldSupportFlags() throws Exception {
        Mono.from(testee.append(mimeMessage, USER, StorageDirective.builder()
            .targetFolder("." + FOLDER)
            .seen(Optional.of(true))
            .build())).block();

        MessageResultIterator messages = mailboxManager.getMailbox(MailboxPath.forUser(USER, FOLDER), session)
            .getMessages(MessageRange.all(), FetchGroup.FULL_CONTENT, session);

        assertThat(messages.next().getFlags().contains(Flags.Flag.SEEN)).isTrue();
    }

    @Test
    void appendShouldReplaceSlashBySeparator() throws Exception {
        Mono.from(testee.append(mimeMessage, USER, StorageDirective.builder()
            .targetFolder(FOLDER + "/any")
            .build())).block();

        MessageResultIterator messages = mailboxManager.getMailbox(MailboxPath.forUser(USER, FOLDER + ".any"), session)
            .getMessages(MessageRange.all(), FetchGroup.FULL_CONTENT, session);

        assertThat(messages).toIterable()
            .hasSize(1);
    }

    @RepeatedTest(20)
    void appendShouldNotFailInConcurrentEnvironment() throws Exception {
        ConcurrentTestRunner.builder()
            .reactorOperation((a, b) -> Mono.from(testee.append(mimeMessage, USER, StorageDirective.builder()
                .targetFolder(FOLDER + "/any")
                .build())).then())
            .threadCount(100)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
    }
}
