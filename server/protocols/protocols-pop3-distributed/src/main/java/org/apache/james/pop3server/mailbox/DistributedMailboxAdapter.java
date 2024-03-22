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

package org.apache.james.pop3server.mailbox;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.DeleteResult;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.protocols.pop3.mailbox.Mailbox;
import org.apache.james.protocols.pop3.mailbox.MessageMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DistributedMailboxAdapter implements Mailbox {
    public static class Factory implements MailboxAdapterFactory {
        private final Pop3MetadataStore metadataStore;
        private final MessageIdManager messageIdManager;
        private final MessageId.Factory messageIdFactory;
        private final MailboxManager mailboxManager;

        @Inject
        public Factory(Pop3MetadataStore metadataStore,
                       MessageIdManager messageIdManager,
                       MessageId.Factory messageIdFactory,
                       @Named("mailboxmanager") MailboxManager mailboxManager) {
            this.metadataStore = metadataStore;
            this.messageIdManager = messageIdManager;
            this.messageIdFactory = messageIdFactory;
            this.mailboxManager = mailboxManager;
        }

        @Override
        public Mailbox create(MessageManager manager, MailboxSession session) {
            return new DistributedMailboxAdapter(metadataStore, messageIdManager, messageIdFactory, mailboxManager, session, manager);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedMailboxAdapter.class);

    private final Pop3MetadataStore metadataStore;
    private final MessageIdManager messageIdManager;
    private final MessageId.Factory messageIdFactory;
    private final MailboxManager mailboxManager;
    private final MailboxSession session;
    private final MessageManager mailbox;

    @Inject
    public DistributedMailboxAdapter(Pop3MetadataStore metadataStore,
                                     MessageIdManager messageIdManager,
                                     MessageId.Factory messageIdFactory,
                                     @Named("mailboxmanager")  MailboxManager mailboxManager,
                                     MailboxSession session,
                                     MessageManager mailbox) {
        this.metadataStore = metadataStore;
        this.messageIdManager = messageIdManager;
        this.messageIdFactory = messageIdFactory;
        this.mailboxManager = mailboxManager;
        this.session = session;
        this.mailbox = mailbox;
    }

    @Override
    public InputStream getMessage(String uid) throws IOException {
        try {
            MessageId messageId = messageIdFactory.fromString(uid);
            Iterator<MessageResult> messages = messageIdManager.getMessage(messageId, FetchGroup.FULL_CONTENT, session).iterator();
            if (messages.hasNext()) {
                return messages.next().getFullContent().getInputStream();
            } else {
                LOGGER.warn("Removing {} from {} POP3 projection for user {} at it is not backed by a MailboxMessage",
                    uid, mailbox.getId().serialize(), session.getUser().asString());
                Mono.from(metadataStore.remove(mailbox.getId(), messageId)).block();
                throw new IOException("Message does not exist for uid " + uid);
            }
        } catch (MailboxException e) {
            throw new IOException("Unable to retrieve message body for uid " + uid, e);
        }
    }

    @Override
    public List<MessageMetaData> getMessages() {
        return Flux.from(metadataStore.stat(mailbox.getId()))
            .map(message -> new MessageMetaData(message.getMessageId().serialize(), message.getSize()))
            .collect(ImmutableList.toImmutableList())
            .block();
    }

    @Override
    public void remove(String... uids) {
        ImmutableList<MessageId> messageIds = Stream.of(uids)
            .map(messageIdFactory::fromString)
            .collect(ImmutableList.toImmutableList());

        Mono.from(messageIdManager.deleteReactive(messageIds, ImmutableList.of(mailbox.getId()), session))
            .flatMapIterable(DeleteResult::getDestroyed)
            // Clear synchronously metadataStore to avoid race condition
            .concatMap(messageId -> Mono.from(metadataStore.remove(mailbox.getId(), messageId)))
            .blockLast();
    }

    @Override
    public String getIdentifier() throws IOException {
        try {
            mailboxManager.startProcessingRequest(session);
            long validity = mailbox.getMailboxEntity()
                .getUidValidity()
                .asLong();
            return Long.toString(validity);
        } catch (MailboxException e) {
            throw new IOException("Unable to retrieve indentifier for mailbox", e);
        } finally {
            mailboxManager.endProcessingRequest(session);
        }
    }

    @Override
    public void close() {
        mailboxManager.endProcessingRequest(session);
    }
}
