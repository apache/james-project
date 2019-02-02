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

package org.apache.mailbox.tools.indexer;

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.indexer.MessageIdReIndexer;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

public class MessageIdReIndexerImpl implements MessageIdReIndexer {
    public static class MessageIdReIndexingTask implements Task {
        private static final Logger LOGGER = LoggerFactory.getLogger(MessageIdReIndexingTask.class);

        public static final String TYPE = "MessageIdReIndexingTask";

        public final class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
            private final MessageId messageId;

            AdditionalInformation(MessageId messageId) {
                this.messageId = messageId;
            }

            public String getMessageId() {
                return messageId.serialize();
            }
        }

        private final MailboxManager mailboxManager;
        private final MailboxSessionMapperFactory mailboxSessionMapperFactory;
        private final ListeningMessageSearchIndex index;
        private final MessageId messageId;
        private final AdditionalInformation additionalInformation;

        MessageIdReIndexingTask(MailboxManager mailboxManager, MailboxSessionMapperFactory mailboxSessionMapperFactory, ListeningMessageSearchIndex index, MessageId messageId) {
            this.mailboxManager = mailboxManager;
            this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
            this.index = index;
            this.messageId = messageId;
            this.additionalInformation = new AdditionalInformation(messageId);
        }

        @Override
        public Result run() {
            try {
                MailboxSession session = mailboxManager.createSystemSession("MessageIdReIndexerImpl");

                return mailboxSessionMapperFactory.getMessageIdMapper(session)
                    .find(ImmutableList.of(messageId), MessageMapper.FetchType.Full)
                    .stream()
                    .map(mailboxMessage -> reIndex(mailboxMessage, session))
                    .reduce(Task::combine)
                    .orElse(Result.COMPLETED);
            } catch (Exception e) {
                LOGGER.warn("Failed to re-index {}", messageId, e);
                return Result.PARTIAL;
            }
        }

        public Result reIndex(MailboxMessage mailboxMessage, MailboxSession session) {
            try {
                MailboxMapper mailboxMapper = mailboxSessionMapperFactory.getMailboxMapper(session);
                Mailbox mailbox = mailboxMapper.findMailboxById(mailboxMessage.getMailboxId());
                index.add(session, mailbox, mailboxMessage);
                return Result.COMPLETED;
            } catch (Exception e) {
                LOGGER.warn("Failed to re-index {} in {}", messageId, mailboxMessage.getMailboxId(), e);
                return Result.PARTIAL;
            }
        }

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public Optional<TaskExecutionDetails.AdditionalInformation> details() {
            return Optional.of(additionalInformation);
        }
    }

    private final MailboxManager mailboxManager;
    private final MailboxSessionMapperFactory mailboxSessionMapperFactory;
    private final ListeningMessageSearchIndex index;

    @Inject
    public MessageIdReIndexerImpl(MailboxManager mailboxManager, MailboxSessionMapperFactory mailboxSessionMapperFactory, ListeningMessageSearchIndex index) {
        this.mailboxManager = mailboxManager;
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
        this.index = index;
    }

    @Override
    public Task reIndex(MessageId messageId) {
        return new MessageIdReIndexingTask(mailboxManager, mailboxSessionMapperFactory, index, messageId);
    }
}
