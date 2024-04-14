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

package org.apache.james.jmap.draft.methods;

import static org.apache.james.jmap.methods.Method.JMAP_PREFIX;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.jmap.draft.exceptions.MailboxHasChildException;
import org.apache.james.jmap.draft.exceptions.SystemMailboxNotUpdatableException;
import org.apache.james.jmap.draft.model.MailboxFactory;
import org.apache.james.jmap.draft.model.SetError;
import org.apache.james.jmap.draft.model.SetMailboxesRequest;
import org.apache.james.jmap.draft.model.SetMailboxesResponse;
import org.apache.james.jmap.draft.model.SetMailboxesResponse.Builder;
import org.apache.james.jmap.draft.model.mailbox.Mailbox;
import org.apache.james.jmap.draft.utils.MailboxUtils;
import org.apache.james.jmap.draft.utils.SortingHierarchicalCollections;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.TooLongMailboxNameException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

public class SetMailboxesDestructionProcessor implements SetMailboxesProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetMailboxesDestructionProcessor.class);

    private final MailboxManager mailboxManager;
    private final SubscriptionManager subscriptionManager;
    private final SortingHierarchicalCollections<Map.Entry<MailboxId, Mailbox>, MailboxId> sortingHierarchicalCollections;
    private final MailboxUtils mailboxUtils;
    private final MailboxFactory mailboxFactory;
    private final MetricFactory metricFactory;

    @Inject
    @VisibleForTesting
    SetMailboxesDestructionProcessor(MailboxManager mailboxManager, SubscriptionManager subscriptionManager, MailboxUtils mailboxUtils, MailboxFactory mailboxFactory, MetricFactory metricFactory) {
        this.mailboxManager = mailboxManager;
        this.subscriptionManager = subscriptionManager;
        this.metricFactory = metricFactory;
        this.sortingHierarchicalCollections =
            new SortingHierarchicalCollections<>(
                    Entry::getKey,
                    x -> x.getValue().getParentId());
        this.mailboxUtils = mailboxUtils;
        this.mailboxFactory = mailboxFactory;
    }

    @Override
    public SetMailboxesResponse process(SetMailboxesRequest request, MailboxSession mailboxSession) {
        TimeMetric timeMetric = metricFactory.timer(JMAP_PREFIX + "SetMailboxesDestructionProcessor");
        ImmutableMap<MailboxId, Mailbox> idToMailbox = mapDestroyRequests(request, mailboxSession);

        SetMailboxesResponse.Builder builder = SetMailboxesResponse.builder();
        sortingHierarchicalCollections.sortFromLeafToRoot(idToMailbox.entrySet())
            .forEach(entry -> destroyMailbox(entry, mailboxSession, builder));

        notDestroyedRequests(request, idToMailbox, builder);
        timeMetric.stopAndPublish();
        return builder.build();
    }

    private ImmutableMap<MailboxId, Mailbox> mapDestroyRequests(SetMailboxesRequest request, MailboxSession mailboxSession) {
        ImmutableMap.Builder<MailboxId, Mailbox> idToMailboxBuilder = ImmutableMap.builder(); 
        request.getDestroy().stream()
            .map(id -> mailboxFactory.builder()
                    .id(id)
                    .session(mailboxSession)
                    .build()
                    .blockOptional())
            .flatMap(Optional::stream)
            .forEach(mailbox -> idToMailboxBuilder.put(mailbox.getId(), mailbox));
        return idToMailboxBuilder.build();
    }

    private void notDestroyedRequests(SetMailboxesRequest request, ImmutableMap<MailboxId, Mailbox> idToMailbox, SetMailboxesResponse.Builder builder) {
        request.getDestroy().stream()
            .filter(id -> !idToMailbox.containsKey(id))
            .forEach(id -> notDestroy(id, builder));
    }

    private void destroyMailbox(Entry<MailboxId, Mailbox> entry, MailboxSession mailboxSession, SetMailboxesResponse.Builder builder) {
        try {
            Mailbox mailbox = entry.getValue();
            preconditions(mailbox, mailboxSession);

            MailboxPath deletedMailbox = mailboxManager.deleteMailbox(mailbox.getId(), mailboxSession).generateAssociatedPath();
            subscriptionManager.unsubscribe(mailboxSession, deletedMailbox);
            builder.destroyed(entry.getKey());
        } catch (MailboxHasChildException e) {
            builder.notDestroyed(entry.getKey(), SetError.builder()
                    .type(SetError.Type.MAILBOX_HAS_CHILD)
                    .description(String.format("The mailbox '%s' has a child.", entry.getKey().serialize()))
                    .build());
        } catch (SystemMailboxNotUpdatableException e) {
            builder.notDestroyed(entry.getKey(), SetError.builder()
                .type(SetError.Type.INVALID_ARGUMENTS)
                .description(String.format("The mailbox '%s' is a system mailbox.", entry.getKey().serialize()))
                .build());
        } catch (TooLongMailboxNameException e) {
            builder.notDestroyed(entry.getKey(), SetError.builder()
                .type(SetError.Type.INVALID_ARGUMENTS)
                .description("The mailbox name length is too long")
                .build());
        } catch (MailboxException e) {
            String message = String.format("An error occurred when deleting the mailbox '%s'", entry.getKey().serialize());
            LOGGER.error(message, e);
            builder.notDestroyed(entry.getKey(), SetError.builder()
                    .type(SetError.Type.ERROR)
                    .description(message)
                    .build());
        }
    }

    private void preconditions(Mailbox mailbox, MailboxSession mailboxSession) throws MailboxHasChildException, SystemMailboxNotUpdatableException, MailboxException {
        checkForChild(mailbox.getId(), mailboxSession);
        checkRole(mailbox.getRole());
    }

    private void checkForChild(MailboxId id, MailboxSession mailboxSession) throws MailboxHasChildException, MailboxException {
        if (mailboxUtils.hasChildren(id, mailboxSession)) {
            throw new MailboxHasChildException();
        }
    }

    private void checkRole(Optional<Role> role) throws SystemMailboxNotUpdatableException {
        if (role.map(Role::isSystemRole).orElse(false)) {
            throw new SystemMailboxNotUpdatableException();
        }
    }

    private void notDestroy(MailboxId id, Builder builder) {
        builder.notDestroyed(id, SetError.builder()
                .type(SetError.Type.NOT_FOUND)
                .description(String.format("The mailbox '%s' was not found.", id.serialize()))
                .build());
    }
}
