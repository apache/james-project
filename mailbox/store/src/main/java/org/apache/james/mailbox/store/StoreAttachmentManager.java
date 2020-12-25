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

package org.apache.james.mailbox.store;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.Username;
import org.apache.james.mailbox.AttachmentManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.ContentType;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.AttachmentMapperFactory;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class StoreAttachmentManager implements AttachmentManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoreAttachmentManager.class);

    private final AttachmentMapperFactory attachmentMapperFactory;
    private final MessageIdManager messageIdManager;

    @Inject
    public StoreAttachmentManager(AttachmentMapperFactory attachmentMapperFactory, MessageIdManager messageIdManager) {
        this.attachmentMapperFactory = attachmentMapperFactory;
        this.messageIdManager = messageIdManager;
    }

    @Override
    public boolean exists(AttachmentId attachmentId, MailboxSession session) throws MailboxException {
        return userHasAccessToAttachment(attachmentId, session);
    }

    @Override
    public AttachmentMetadata getAttachment(AttachmentId attachmentId, MailboxSession mailboxSession) throws MailboxException, AttachmentNotFoundException {
        if (!userHasAccessToAttachment(attachmentId, mailboxSession)) {
            throw new AttachmentNotFoundException(attachmentId.getId());
        }
        return attachmentMapperFactory.getAttachmentMapper(mailboxSession).getAttachment(attachmentId);
    }

    @Override
    public List<AttachmentMetadata> getAttachments(List<AttachmentId> attachmentIds, MailboxSession mailboxSession) throws MailboxException {
        Collection<AttachmentId> accessibleAttachmentIds = keepAccessible(attachmentIds, mailboxSession);

        return attachmentMapperFactory.getAttachmentMapper(mailboxSession).getAttachments(accessibleAttachmentIds);
    }

    @Override
    public Publisher<AttachmentMetadata> storeAttachment(ContentType contentType, InputStream attachmentContent, MailboxSession mailboxSession) {
        return attachmentMapperFactory.getAttachmentMapper(mailboxSession)
            .storeAttachmentForOwner(contentType, attachmentContent, mailboxSession.getUser());
    }

    private boolean userHasAccessToAttachment(AttachmentId attachmentId, MailboxSession mailboxSession) {
        try {
            return isExplicitlyAOwner(attachmentId, mailboxSession)
                || isReferencedInUserMessages(attachmentId, mailboxSession);
        } catch (MailboxException e) {
            LOGGER.warn("Error while checking attachment related accessible message ids", e);
            throw new RuntimeException(e);
        }
    }

    private Collection<AttachmentId> keepAccessible(Collection<AttachmentId> attachmentIds, MailboxSession mailboxSession) {
        try {
            Set<AttachmentId> referencedByMessages = referencedInUserMessages(attachmentIds, mailboxSession);
            ImmutableSet<AttachmentId> owned = Sets.difference(ImmutableSet.copyOf(attachmentIds), referencedByMessages)
                .stream()
                .filter(Throwing.<AttachmentId>predicate(id -> isExplicitlyAOwner(id, mailboxSession)).sneakyThrow())
                .collect(Guavate.toImmutableSet());

            return ImmutableSet.<AttachmentId>builder()
                .addAll(referencedByMessages)
                .addAll(owned)
                .build();
        } catch (MailboxException e) {
            LOGGER.warn("Error while checking attachment related accessible message ids", e);
            throw new RuntimeException(e);
        }
    }

    private boolean isReferencedInUserMessages(AttachmentId attachmentId, MailboxSession mailboxSession) throws MailboxException {
        Collection<MessageId> relatedMessageIds = getRelatedMessageIds(attachmentId, mailboxSession);
        return !messageIdManager
            .accessibleMessages(relatedMessageIds, mailboxSession)
            .isEmpty();
    }

    private Set<AttachmentId> referencedInUserMessages(Collection<AttachmentId> attachmentIds, MailboxSession mailboxSession) throws MailboxException {
        Map<MessageId, Collection<AttachmentId>> entries = attachmentIds.stream()
            .flatMap(Throwing.<AttachmentId, Stream<Pair<MessageId, AttachmentId>>>function(
                attachmentId -> getRelatedMessageIds(attachmentId, mailboxSession).stream()
                    .map(messageId -> Pair.of(messageId, attachmentId)))
                .sneakyThrow())
            .collect(Guavate.toImmutableListMultimap(
                Pair::getKey,
                Pair::getValue))
            .asMap();

        Set<MessageId> accessibleMessages = messageIdManager.accessibleMessages(entries.keySet(), mailboxSession);

        return entries.entrySet().stream()
            .filter(entry -> accessibleMessages.contains(entry.getKey()))
            .flatMap(entry -> entry.getValue().stream())
            .collect(Guavate.toImmutableSet());
    }

    private boolean isExplicitlyAOwner(AttachmentId attachmentId, MailboxSession mailboxSession) throws MailboxException {
        Collection<Username> explicitOwners = attachmentMapperFactory.getAttachmentMapper(mailboxSession)
            .getOwners(attachmentId);
        return explicitOwners.stream()
            .anyMatch(username -> mailboxSession.getUser().equals(username));
    }

    private Collection<MessageId> getRelatedMessageIds(AttachmentId attachmentId, MailboxSession mailboxSession) throws MailboxException {
        return attachmentMapperFactory.getAttachmentMapper(mailboxSession).getRelatedMessageIds(attachmentId);
    }

    @Override
    public InputStream loadAttachmentContent(AttachmentId attachmentId, MailboxSession mailboxSession) throws AttachmentNotFoundException, IOException {
        if (!userHasAccessToAttachment(attachmentId, mailboxSession)) {
            throw new AttachmentNotFoundException(attachmentId.getId());
        }
        return attachmentMapperFactory.getAttachmentMapper(mailboxSession).loadAttachmentContent(attachmentId);
    }
}
