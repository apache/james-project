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

import static org.apache.james.backends.cassandra.init.configuration.CassandraConsistenciesConfiguration.ConsistencyChoice;
import static org.apache.james.backends.cassandra.init.configuration.CassandraConsistenciesConfiguration.ConsistencyChoice.STRONG;
import static org.apache.james.backends.cassandra.init.configuration.CassandraConsistenciesConfiguration.ConsistencyChoice.WEAK;
import static org.apache.james.util.FunctionalUtils.negate;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.util.Optional;
import java.util.function.Predicate;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraACLMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraApplicableFlagDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentDAOV2;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentOwnerDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraDeletedMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraFirstUnseenDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxCounterDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMailboxRecentsDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAOV3;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraUserMailboxRightsDAO;
import org.apache.james.mailbox.cassandra.mail.MessageAttachmentRepresentation;
import org.apache.james.mailbox.cassandra.mail.MessageRepresentation;
import org.apache.james.mailbox.events.MailboxEvents.Expunged;
import org.apache.james.mailbox.events.MailboxEvents.MailboxDeletion;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.util.streams.Limit;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * This listener cleans Cassandra metadata up. It retrieves dandling unreferenced metadata after the delete operation
 * had been conducted out. Then it deletes the lower levels first so that upon failures undeleted metadata can still be
 * reached.
 *
 * This cleanup is not needed for strict correctness from a MailboxManager point of view thus it could be carried out
 * asynchronously, via mailbox listeners so that it can be retried.
 *
 * Mailbox listener failures lead to eventBus retrying their execution, it ensures the result of the deletion to be
 * idempotent.
 */
public class DeleteMessageListener implements EventListener.ReactiveGroupEventListener {
    private static final Optional<CassandraId> ALL_MAILBOXES = Optional.empty();

    public static class DeleteMessageListenerGroup extends Group {

    }

    private final CassandraMessageIdToImapUidDAO imapUidDAO;
    private final CassandraMessageIdDAO messageIdDAO;
    private final CassandraMessageDAO messageDAO;
    private final CassandraMessageDAOV3 messageDAOV3;
    private final CassandraAttachmentDAOV2 attachmentDAO;
    private final CassandraAttachmentOwnerDAO ownerDAO;
    private final CassandraAttachmentMessageIdDAO attachmentMessageIdDAO;
    private final CassandraACLMapper aclMapper;
    private final CassandraUserMailboxRightsDAO rightsDAO;
    private final CassandraApplicableFlagDAO applicableFlagDAO;
    private final CassandraFirstUnseenDAO firstUnseenDAO;
    private final CassandraDeletedMessageDAO deletedMessageDAO;
    private final CassandraMailboxCounterDAO counterDAO;
    private final CassandraMailboxRecentsDAO recentsDAO;
    private final BlobStore blobStore;
    private final CassandraConfiguration cassandraConfiguration;

    @Inject
    public DeleteMessageListener(CassandraMessageIdToImapUidDAO imapUidDAO, CassandraMessageIdDAO messageIdDAO, CassandraMessageDAO messageDAO,
                                 CassandraMessageDAOV3 messageDAOV3, CassandraAttachmentDAOV2 attachmentDAO, CassandraAttachmentOwnerDAO ownerDAO,
                                 CassandraAttachmentMessageIdDAO attachmentMessageIdDAO, CassandraACLMapper aclMapper,
                                 CassandraUserMailboxRightsDAO rightsDAO, CassandraApplicableFlagDAO applicableFlagDAO,
                                 CassandraFirstUnseenDAO firstUnseenDAO, CassandraDeletedMessageDAO deletedMessageDAO,
                                 CassandraMailboxCounterDAO counterDAO, CassandraMailboxRecentsDAO recentsDAO, BlobStore blobStore,
                                 CassandraConfiguration cassandraConfiguration) {
        this.imapUidDAO = imapUidDAO;
        this.messageIdDAO = messageIdDAO;
        this.messageDAO = messageDAO;
        this.messageDAOV3 = messageDAOV3;
        this.attachmentDAO = attachmentDAO;
        this.ownerDAO = ownerDAO;
        this.attachmentMessageIdDAO = attachmentMessageIdDAO;
        this.aclMapper = aclMapper;
        this.rightsDAO = rightsDAO;
        this.applicableFlagDAO = applicableFlagDAO;
        this.firstUnseenDAO = firstUnseenDAO;
        this.deletedMessageDAO = deletedMessageDAO;
        this.counterDAO = counterDAO;
        this.recentsDAO = recentsDAO;
        this.blobStore = blobStore;
        this.cassandraConfiguration = cassandraConfiguration;
    }

    @Override
    public Group getDefaultGroup() {
        return new DeleteMessageListenerGroup();
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof Expunged || event instanceof MailboxDeletion;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof Expunged) {
            Expunged expunged = (Expunged) event;

            return handleMessageDeletion(expunged);
        }
        if (event instanceof MailboxDeletion) {
            MailboxDeletion mailboxDeletion = (MailboxDeletion) event;

            CassandraId mailboxId = (CassandraId) mailboxDeletion.getMailboxId();

            return handleMailboxDeletion(mailboxId);
        }
        return Mono.empty();
    }

    private Mono<Void> handleMailboxDeletion(CassandraId mailboxId) {
        int prefetch = 1;
        return Flux.mergeDelayError(prefetch,
                messageIdDAO.retrieveMessages(mailboxId, MessageRange.all(), Limit.unlimited())
                    .map(ComposedMessageIdWithMetaData::getComposedMessageId)
                    .concatMap(metadata -> handleMessageDeletionAsPartOfMailboxDeletion((CassandraMessageId) metadata.getMessageId(), mailboxId)
                        .then(imapUidDAO.delete((CassandraMessageId) metadata.getMessageId(), mailboxId))
                        .then(messageIdDAO.delete(mailboxId, metadata.getUid()))),
                deleteAcl(mailboxId),
                applicableFlagDAO.delete(mailboxId),
                firstUnseenDAO.removeAll(mailboxId),
                deletedMessageDAO.removeAll(mailboxId),
                counterDAO.delete(mailboxId),
                recentsDAO.delete(mailboxId))
            .then();
    }

    private Mono<Void> handleMessageDeletion(Expunged expunged) {
        return Flux.fromIterable(expunged.getExpunged()
            .values())
            .map(MessageMetaData::getMessageId)
            .map(CassandraMessageId.class::cast)
            .concatMap(this::handleMessageDeletion)
            .then();
    }

    private Mono<Void> deleteAcl(CassandraId mailboxId) {
        return aclMapper.getACL(mailboxId)
            .flatMap(acl -> rightsDAO.update(mailboxId, ACLDiff.computeDiff(acl, MailboxACL.EMPTY))
                .then(aclMapper.delete(mailboxId)));
    }

    private Mono<Void> handleMessageDeletion(CassandraMessageId messageId) {
        return Mono.just(messageId)
            .filterWhen(this::isReferenced)
            .flatMap(id -> readMessage(id)
                .flatMap(message -> deleteUnreferencedAttachments(message).thenReturn(message))
                .flatMap(this::deleteMessageBlobs)
                .flatMap(this::deleteAttachmentMessageIds)
                .then(messageDAO.delete(messageId))
                .then(messageDAOV3.delete(messageId)));
    }

    private Mono<Void> handleMessageDeletionAsPartOfMailboxDeletion(CassandraMessageId messageId, CassandraId excludedId) {
        return Mono.just(messageId)
            .filterWhen(id -> isReferenced(id, excludedId))
            .flatMap(id -> readMessage(id)
                .flatMap(message -> deleteUnreferencedAttachments(message).thenReturn(message))
                .flatMap(this::deleteMessageBlobs)
                .flatMap(this::deleteAttachmentMessageIds)
                .then(messageDAO.delete(messageId))
                .then(messageDAOV3.delete(messageId)));
    }

    private Mono<MessageRepresentation> deleteMessageBlobs(MessageRepresentation message) {
        return Flux.merge(
                blobStore.delete(blobStore.getDefaultBucketName(), message.getHeaderId()),
                blobStore.delete(blobStore.getDefaultBucketName(), message.getBodyId()))
            .then()
            .thenReturn(message);
    }

    private Mono<MessageRepresentation> readMessage(CassandraMessageId id) {
        return messageDAOV3.retrieveMessage(id, MessageMapper.FetchType.Metadata)
            .switchIfEmpty(messageDAO.retrieveMessage(id, MessageMapper.FetchType.Metadata));
    }

    private Mono<Void> deleteUnreferencedAttachments(MessageRepresentation message) {
        return Flux.fromIterable(message.getAttachments())
            .filterWhen(attachment -> ownerDAO.retrieveOwners(attachment.getAttachmentId()).hasElements().map(negate()), DEFAULT_CONCURRENCY)
            .filterWhen(attachment -> hasOtherMessagesReferences(message, attachment), DEFAULT_CONCURRENCY)
            .concatMap(attachment -> attachmentDAO.getAttachment(attachment.getAttachmentId())
                .map(CassandraAttachmentDAOV2.DAOAttachment::getBlobId)
                .flatMap(blobId -> Mono.from(blobStore.delete(blobStore.getDefaultBucketName(), blobId)))
                .then(attachmentDAO.delete(attachment.getAttachmentId())))
            .then();
    }

    private Mono<Void> deleteAttachmentMessageIds(MessageRepresentation message) {
        return Flux.fromIterable(message.getAttachments())
            .concatMap(attachment -> attachmentMessageIdDAO.delete(attachment.getAttachmentId(), message.getMessageId()))
            .then();
    }

    private Mono<Boolean> hasOtherMessagesReferences(MessageRepresentation message, MessageAttachmentRepresentation attachment) {
        return attachmentMessageIdDAO.getOwnerMessageIds(attachment.getAttachmentId())
            .filter(Predicate.not(Predicate.isEqual(message.getMessageId())))
            .hasElements()
            .map(negate());
    }

    private Mono<Boolean> isReferenced(CassandraMessageId id) {
        return imapUidDAO.retrieve(id, ALL_MAILBOXES, chooseReadConsistencyUponWrites())
            .hasElements()
            .map(negate());
    }

    private Mono<Boolean> isReferenced(CassandraMessageId id, CassandraId excludedId) {
        return imapUidDAO.retrieve(id, ALL_MAILBOXES, chooseReadConsistencyUponWrites())
            .filter(metadata -> !metadata.getComposedMessageId().getMailboxId().equals(excludedId))
            .hasElements()
            .map(negate());
    }

    private ConsistencyChoice chooseReadConsistencyUponWrites() {
        if (cassandraConfiguration.isMessageWriteStrongConsistency()) {
            return STRONG;
        }
        return WEAK;
    }
}
