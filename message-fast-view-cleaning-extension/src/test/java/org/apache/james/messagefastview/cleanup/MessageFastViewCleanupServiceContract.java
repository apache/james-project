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

package org.apache.james.messagefastview.cleanup;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.Preview;
import org.apache.james.jmap.api.projections.MessageFastViewPrecomputedProperties;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.task.Task;
import org.apache.james.user.api.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;

public interface MessageFastViewCleanupServiceContract {
    Username USER_1 = Username.of("user1");
    String PASSWORD = "password";
    MailboxPath MAILBOX_PATH = MailboxPath.forUser(USER_1, "mailbox");
    Preview PREVIEW_1 = Preview.from("preview 1");
    Preview PREVIEW_2 = Preview.from("preview 2");
    MessageFastViewPrecomputedProperties MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_1 = MessageFastViewPrecomputedProperties.builder()
        .preview(PREVIEW_1)
        .hasAttachment()
        .build();
    MessageFastViewPrecomputedProperties MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_2 = MessageFastViewPrecomputedProperties.builder()
        .preview(PREVIEW_2)
        .noAttachments()
        .build();

    UsersRepository usersRepository();

    MailboxManager mailboxManager();

    SessionProvider sessionProvider();

    MessageFastViewProjection messageFastViewProjection();

    MessageId.Factory messageIdFactory();

    MessageFastViewCleanupService testee();

    @Test
    default void cleanupShouldReturnCompleteWhenNoData() throws Exception {
        assertThat(testee().cleanup(new MessageFastViewCleanupService.Context(), MessageFastViewCleanupService.RunningOptions.DEFAULT).block())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    default void cleanupShouldDeleteRedundantMessageFastViews() throws Exception {
        usersRepository().addUser(USER_1, PASSWORD);
        MailboxSession session = sessionProvider().createSystemSession(USER_1);
        mailboxManager().createMailbox(MAILBOX_PATH, session);

        MessageManager messageManager = mailboxManager().getMailbox(MAILBOX_PATH, session);
        MessageId messageId = appendAMessageForUser(messageManager, session);
        messageFastViewProjection().store(messageId, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_1);
        MessageId messageId2 = messageIdFactory().generate();
        messageFastViewProjection().store(messageId2, MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_2);

        testee().cleanup(new MessageFastViewCleanupService.Context(), MessageFastViewCleanupService.RunningOptions.DEFAULT).block();
        assertThat(Flux.from(messageFastViewProjection().getAllMessageIds()).collectList().block())
            .containsExactly(messageId);
    }

    default MessageId appendAMessageForUser(MessageManager messageManager, MailboxSession session) throws Exception {
        String recipient = "test@localhost.com";
        String body = "This is a message";
        return messageManager.appendMessage(MessageManager.AppendCommand.from(
                Message.Builder.of()
                    .setTo(recipient)
                    .setBody(body, StandardCharsets.UTF_8)),
            session)
            .getId()
            .getMessageId();
    }
}
