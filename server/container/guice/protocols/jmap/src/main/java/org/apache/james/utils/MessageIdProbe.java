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

package org.apache.james.utils;

import java.util.List;

import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager.FlagsUpdateMode;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public class MessageIdProbe implements GuiceProbe {
    private final MailboxManager mailboxManager;
    private final MessageIdManager messageIdManager;

    @Inject
    public MessageIdProbe(MailboxManager mailboxManager, MessageIdManager messageIdManager) {
        this.mailboxManager = mailboxManager;
        this.messageIdManager = messageIdManager;
    }

    public List<MessageResult> getMessages(MessageId messageId, String user) throws MailboxException {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(user);

        return messageIdManager.getMessages(ImmutableList.of(messageId), FetchGroupImpl.FULL_CONTENT, mailboxSession);
    }

    public void updateNewFlags(String user, Flags newFlags, MessageId messageId, List<MailboxId> mailboxIds) throws MailboxException {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(user);

        messageIdManager.setFlags(newFlags, FlagsUpdateMode.REPLACE, messageId, mailboxIds, mailboxSession);
    }

    public List<AttachmentId> retrieveAttachmentIds(MessageId messageId, String username) throws MailboxException {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);
        List<MessageResult> messages = messageIdManager.getMessages(
            ImmutableList.of(messageId),
            FetchGroupImpl.MINIMAL,
            mailboxSession);

        return messages.stream()
            .flatMap(Throwing.function(messageResult -> messageResult.getAttachments().stream()))
            .map(MessageAttachment::getAttachmentId)
            .collect(Guavate.toImmutableList());
    }
}
