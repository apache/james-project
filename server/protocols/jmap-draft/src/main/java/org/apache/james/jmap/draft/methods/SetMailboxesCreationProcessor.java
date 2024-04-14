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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.jmap.draft.exceptions.MailboxNotOwnedException;
import org.apache.james.jmap.draft.exceptions.MailboxParentNotFoundException;
import org.apache.james.jmap.draft.model.MailboxCreationId;
import org.apache.james.jmap.draft.model.MailboxFactory;
import org.apache.james.jmap.draft.model.SetError;
import org.apache.james.jmap.draft.model.SetMailboxesRequest;
import org.apache.james.jmap.draft.model.SetMailboxesResponse;
import org.apache.james.jmap.draft.model.mailbox.Mailbox;
import org.apache.james.jmap.draft.model.mailbox.MailboxCreateRequest;
import org.apache.james.jmap.draft.utils.DependencyGraph.CycleDetectedException;
import org.apache.james.jmap.draft.utils.SortingHierarchicalCollections;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.InboxAlreadyCreated;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNameException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.TooLongMailboxNameException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxId.Factory;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.functions.FunctionChainer;
import com.google.common.annotations.VisibleForTesting;

public class SetMailboxesCreationProcessor implements SetMailboxesProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetMailboxesCreationProcessor.class);

    private final MailboxManager mailboxManager;
    private final SortingHierarchicalCollections<Map.Entry<MailboxCreationId, MailboxCreateRequest>, MailboxCreationId> sortingHierarchicalCollections;
    private final MailboxFactory mailboxFactory;
    private final Factory mailboxIdFactory;
    private final MetricFactory metricFactory;

    @Inject
    @VisibleForTesting
    SetMailboxesCreationProcessor(MailboxManager mailboxManager, MailboxFactory mailboxFactory, Factory mailboxIdFactory, MetricFactory metricFactory) {
        this.mailboxManager = mailboxManager;
        this.metricFactory = metricFactory;
        this.sortingHierarchicalCollections =
            new SortingHierarchicalCollections<>(
                Map.Entry::getKey,
                x -> x.getValue().getParentId());
        this.mailboxFactory = mailboxFactory;
        this.mailboxIdFactory = mailboxIdFactory;
    }

    @Override
    public SetMailboxesResponse process(SetMailboxesRequest request, MailboxSession mailboxSession) {
        TimeMetric timeMetric = metricFactory.timer(JMAP_PREFIX + "SetMailboxesCreationProcessor");

        SetMailboxesResponse.Builder builder = SetMailboxesResponse.builder();
        try {
            Map<MailboxCreationId, MailboxId> creationIdsToCreatedMailboxId = new HashMap<>();
            sortingHierarchicalCollections.sortFromRootToLeaf(request.getCreate().entrySet())
                .forEach(entry -> 
                    createMailbox(entry.getKey(), entry.getValue(), mailboxSession, creationIdsToCreatedMailboxId, builder));
        } catch (CycleDetectedException e) {
            markRequestsAsNotCreatedDueToCycle(request, builder);
        }

        timeMetric.stopAndPublish();
        return builder.build();
    }

    private void markRequestsAsNotCreatedDueToCycle(SetMailboxesRequest request, SetMailboxesResponse.Builder builder) {
        request.getCreate().forEach((key, value) ->
            builder.notCreated(
                key,
                SetError.builder()
                    .type(SetError.Type.INVALID_ARGUMENTS)
                    .description("The created mailboxes introduce a cycle.")
                    .build()));
    }

    private void createMailbox(MailboxCreationId mailboxCreationId, MailboxCreateRequest mailboxRequest, MailboxSession mailboxSession,
            Map<MailboxCreationId, MailboxId> creationIdsToCreatedMailboxId, SetMailboxesResponse.Builder builder) {
        try {
            ensureValidMailboxName(mailboxRequest, mailboxSession);
            MailboxPath mailboxPath = computeMailboxPath(mailboxRequest, creationIdsToCreatedMailboxId, mailboxSession);
            Optional<MailboxId> mailboxId = mailboxManager.createMailbox(mailboxPath, MailboxManager.CreateOption.CREATE_SUBSCRIPTION, mailboxSession);
            Optional<Mailbox> mailbox = mailboxId.flatMap(id -> mailboxFactory.builder()
                    .id(id)
                    .session(mailboxSession)
                    .build()
                    .blockOptional());
            if (mailbox.isPresent()) {
                builder.created(mailboxCreationId, mailbox.get());
                creationIdsToCreatedMailboxId.put(mailboxCreationId, mailbox.get().getId());
            } else {
                builder.notCreated(mailboxCreationId, SetError.builder()
                    .type(SetError.Type.ERROR)
                    .description("An error occurred when creating the mailbox")
                    .build());
            }
        } catch (TooLongMailboxNameException e) {
            builder.notCreated(mailboxCreationId, SetError.builder()
                .type(SetError.Type.INVALID_ARGUMENTS)
                .description("The mailbox name length is too long")
                .build());
        } catch (MailboxNotOwnedException e) {
            builder.notCreated(mailboxCreationId, SetError.builder()
                .type(SetError.Type.INVALID_ARGUMENTS)
                .description("The mailbox can not be created with a parent mailbox belonging to another user")
                .build());
        } catch (MailboxNameException | MailboxParentNotFoundException e) {
            builder.notCreated(mailboxCreationId, SetError.builder()
                    .type(SetError.Type.INVALID_ARGUMENTS)
                    .description(e.getMessage())
                    .build());
        } catch (InboxAlreadyCreated e) {
            String message = String.format("The mailbox '%s' already exists as 'INBOX'", e.getMailboxName());
            LOGGER.error(message, e);
            builder.notCreated(mailboxCreationId, SetError.builder()
                .type(SetError.Type.INVALID_ARGUMENTS)
                .description(message)
                .build());
        } catch (MailboxExistsException e) {
            String message = String.format("The mailbox '%s' already exists.", mailboxCreationId.getCreationId());
            builder.notCreated(mailboxCreationId, SetError.builder()
                    .type(SetError.Type.INVALID_ARGUMENTS)
                    .description(message)
                    .build());
        } catch (MailboxException e) {
            String message = String.format("An error occurred when creating the mailbox '%s'", mailboxCreationId.getCreationId());
            LOGGER.error(message, e);
            builder.notCreated(mailboxCreationId, SetError.builder()
                .type(SetError.Type.ERROR)
                .description(message)
                .build());
        }
    }

    private void ensureValidMailboxName(MailboxCreateRequest mailboxRequest, MailboxSession mailboxSession) throws MailboxNameException {
        String name = mailboxRequest.getName();
        char pathDelimiter = mailboxSession.getPathDelimiter();
        if (name.contains(String.valueOf(pathDelimiter))) {
            throw new MailboxNameException(String.format("The mailbox '%s' contains an illegal character: '%c'", name, pathDelimiter));
        }
    }

    private MailboxPath computeMailboxPath(MailboxCreateRequest mailboxRequest, Map<MailboxCreationId, MailboxId> creationIdsToCreatedMailboxId, MailboxSession mailboxSession) throws MailboxException {
        if (mailboxRequest.getParentId().isPresent()) {
            MailboxCreationId parentId = mailboxRequest.getParentId().get();
            MailboxPath parentPath = getMailboxPath(creationIdsToCreatedMailboxId, mailboxSession, parentId);

            assertBelongsToUser(parentPath, mailboxSession);

            return MailboxPath.forUser(mailboxSession.getUser(),
                parentPath.getName() + mailboxSession.getPathDelimiter() + mailboxRequest.getName());
        }
        return MailboxPath.forUser(mailboxSession.getUser(), mailboxRequest.getName());
    }

    private void assertBelongsToUser(MailboxPath mailboxPath, MailboxSession mailboxSession) throws MailboxNotOwnedException {
        if (!mailboxPath.belongsTo(mailboxSession)) {
            throw new MailboxNotOwnedException();
        }
    }

    private MailboxPath getMailboxPath(Map<MailboxCreationId, MailboxId> creationIdsToCreatedMailboxId, MailboxSession mailboxSession, MailboxCreationId parentId) throws MailboxException {
        Optional<MailboxId> mailboxId = readCreationIdAsMailboxId(parentId)
            .or(() -> Optional.ofNullable(creationIdsToCreatedMailboxId.get(parentId)));

        return getMailboxPathFromId(mailboxId, mailboxSession)
                .orElseThrow(() -> new MailboxParentNotFoundException(parentId));
    }

    private Optional<MailboxId> readCreationIdAsMailboxId(MailboxCreationId creationId) {
        try {
            return Optional.of(mailboxIdFactory.fromString(creationId.getCreationId()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @VisibleForTesting
    Optional<MailboxPath> getMailboxPathFromId(Optional<MailboxId> mailboxId, MailboxSession mailboxSession) {
        FunctionChainer<MailboxId, Optional<MailboxPath>> fromMailboxIdToMailboxPath = Throwing.function(id -> {
            try {
                return Optional.of(mailboxManager.getMailbox(id, mailboxSession).getMailboxPath());
            } catch (MailboxNotFoundException e) {
                return Optional.empty();
            }
        });
        return mailboxId
            .flatMap(fromMailboxIdToMailboxPath.sneakyThrow());
    }

}
