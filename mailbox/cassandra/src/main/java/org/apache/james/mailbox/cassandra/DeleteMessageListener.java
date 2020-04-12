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

package org.apache.james.mailbox.cassandra;

import static org.apache.james.util.FunctionalUtils.negate;

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentOwnerDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.MessageAttachmentRepresentation;
import org.apache.james.mailbox.cassandra.mail.MessageRepresentation;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.Group;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.mail.MessageMapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DeleteMessageListener implements MailboxListener.GroupMailboxListener {
    private static final Optional<CassandraId> ALL_MAILBOXES = Optional.empty();

    public static class DeleteMessageListenerGroup extends Group {

    }

    @Inject
    public DeleteMessageListener(CassandraMessageIdToImapUidDAO imapUidDAO, CassandraMessageIdDAO messageIdDAO, CassandraMessageDAO messageDAO,
                                 CassandraAttachmentDAOV2 attachmentDAO, CassandraAttachmentOwnerDAO ownerDAO,
                                 CassandraAttachmentMessageIdDAO attachmentMessageIdDAO) {
        this.imapUidDAO = imapUidDAO;
        this.messageIdDAO = messageIdDAO;
        this.messageDAO = messageDAO;
        this.attachmentDAO = attachmentDAO;
        this.ownerDAO = ownerDAO;
        this.attachmentMessageIdDAO = attachmentMessageIdDAO;
    }

    private final CassandraMessageIdToImapUidDAO imapUidDAO;
    private final CassandraMessageIdDAO messageIdDAO;
    private final CassandraMessageDAO messageDAO;
    private final CassandraAttachmentDAOV2 attachmentDAO;
    private final CassandraAttachmentOwnerDAO ownerDAO;
    private final CassandraAttachmentMessageIdDAO attachmentMessageIdDAO;

    @Override
    public Group getDefaultGroup() {
        return new DeleteMessageListenerGroup();
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof Expunged || event instanceof MailboxDeletion;
    }

    @Override
    public void event(Event event) {
        if (event instanceof Expunged) {
            Expunged expunged = (Expunged) event;

            Flux.fromIterable(expunged.getExpunged()
                .values())
                .map(MessageMetaData::getMessageId)
                .map(CassandraMessageId.class::cast)
                .concatMap(this::handleDeletion)
                .then()
                .block();
        }
        if (event instanceof MailboxDeletion) {
            MailboxDeletion mailboxDeletion = (MailboxDeletion) event;

            CassandraId mailboxId = (CassandraId) mailboxDeletion.getMailboxId();

            messageIdDAO.retrieveMessages(mailboxId, MessageRange.all())
                .map(ComposedMessageIdWithMetaData::getComposedMessageId)
                .concatMap(metadata -> imapUidDAO.delete((CassandraMessageId) metadata.getMessageId(), mailboxId)
                    .then(messageIdDAO.delete(mailboxId, metadata.getUid()))
                    .then(handleDeletion((CassandraMessageId) metadata.getMessageId())))
                .then()
                .block();
        }
    }

    private Mono<Void> handleDeletion(CassandraMessageId messageId) {
        return Mono.just(messageId)
            .filterWhen(this::isReferenced)
            .flatMap(id -> readMessage(id)
                .flatMap(message -> deleteUnreferencedAttachments(message).thenReturn(message))
                .flatMap(this::deleteAttachmentMessageIds)
                .then(messageDAO.delete(messageId)));
    }

    private Mono<MessageRepresentation> readMessage(CassandraMessageId id) {
        return messageDAO.retrieveMessage(id, MessageMapper.FetchType.Metadata);
    }

    private Mono<Void> deleteUnreferencedAttachments(MessageRepresentation message) {
        return Flux.fromIterable(message.getAttachments())
            .filterWhen(attachment -> ownerDAO.retrieveOwners(attachment.getAttachmentId()).hasElements().map(negate()))
            .filterWhen(attachment -> hasOtherMessagesReferences(message, attachment))
            .concatMap(attachment -> attachmentDAO.delete(attachment.getAttachmentId()))
            .then();
    }

    private Mono<Void> deleteAttachmentMessageIds(MessageRepresentation message) {
        return Flux.fromIterable(message.getAttachments())
            .concatMap(attachment -> attachmentMessageIdDAO.delete(attachment.getAttachmentId(), message.getMessageId()))
            .then();
    }

    private Mono<Boolean> hasOtherMessagesReferences(MessageRepresentation message, MessageAttachmentRepresentation attachment) {
        return attachmentMessageIdDAO.getOwnerMessageIds(attachment.getAttachmentId())
            .filter(messageId -> !message.getMessageId().equals(messageId))
            .hasElements()
            .map(negate());
    }

    private Mono<Boolean> isReferenced(CassandraMessageId id) {
        return imapUidDAO.retrieve(id, ALL_MAILBOXES)
            .hasElements()
            .map(negate());
    }
}
