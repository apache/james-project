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

package org.apache.james.jmap.methods;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.jmap.exceptions.MailboxNameException;
import org.apache.james.jmap.exceptions.MailboxParentNotFoundException;
import org.apache.james.jmap.model.MailboxCreationId;
import org.apache.james.jmap.model.SetError;
import org.apache.james.jmap.model.SetMailboxesRequest;
import org.apache.james.jmap.model.SetMailboxesResponse;
import org.apache.james.jmap.model.mailbox.Mailbox;
import org.apache.james.jmap.model.mailbox.MailboxRequest;
import org.apache.james.jmap.utils.SortingHierarchicalCollections;
import org.apache.james.jmap.utils.DependencyGraph.CycleDetectedException;
import org.apache.james.jmap.utils.MailboxUtils;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;

public class SetMailboxesCreationProcessor<Id extends MailboxId> implements SetMailboxesProcessor<Id> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetMailboxesCreationProcessor.class);

    private final MailboxManager mailboxManager;
    private final SortingHierarchicalCollections<Map.Entry<MailboxCreationId, MailboxRequest>, String> sortingHierarchicalCollections;
    private final MailboxUtils<Id> mailboxUtils;

    @Inject
    @VisibleForTesting
    SetMailboxesCreationProcessor(MailboxManager mailboxManager, MailboxUtils<Id> mailboxUtils) {
        this.mailboxManager = mailboxManager;
        this.sortingHierarchicalCollections =
            new SortingHierarchicalCollections<>(
                x -> x.getKey().getCreationId(),
                x -> x.getValue().getParentId());
        this.mailboxUtils = mailboxUtils;
    }

    public SetMailboxesResponse process(SetMailboxesRequest request, MailboxSession mailboxSession) {
        SetMailboxesResponse.Builder builder = SetMailboxesResponse.builder();
        try {
            Map<MailboxCreationId, String> creationIdsToCreatedMailboxId = new HashMap<>();
            sortingHierarchicalCollections.sortFromRootToLeaf(request.getCreate().entrySet())
                .forEach(entry -> 
                    createMailbox(entry.getKey(), entry.getValue(), mailboxSession, creationIdsToCreatedMailboxId, builder));
        } catch (CycleDetectedException e) {
            markRequestsAsNotCreatedDueToCycle(request, builder);
        }
        return builder.build();
    }

    private void markRequestsAsNotCreatedDueToCycle(SetMailboxesRequest request, SetMailboxesResponse.Builder builder) {
        request.getCreate().entrySet()
            .forEach(entry ->
                builder.notCreated(entry.getKey(),
                        SetError.builder()
                        .type("invalidArguments")
                        .description("The created mailboxes introduce a cycle.")
                        .build()));
    }

    private void createMailbox(MailboxCreationId mailboxCreationId, MailboxRequest mailboxRequest, MailboxSession mailboxSession,
            Map<MailboxCreationId, String> creationIdsToCreatedMailboxId, SetMailboxesResponse.Builder builder) {
        try {
            ensureValidMailboxName(mailboxRequest, mailboxSession);
            MailboxPath mailboxPath = getMailboxPath(mailboxRequest, creationIdsToCreatedMailboxId, mailboxSession);
            mailboxManager.createMailbox(mailboxPath, mailboxSession);
            Optional<Mailbox> mailbox = mailboxUtils.mailboxFromMailboxPath(mailboxPath, mailboxSession);
            if (mailbox.isPresent()) {
                builder.creation(mailboxCreationId, mailbox.get());
                creationIdsToCreatedMailboxId.put(mailboxCreationId, mailbox.get().getId());
            } else {
                builder.notCreated(mailboxCreationId, SetError.builder()
                        .type("anErrorOccurred")
                        .description("An error occurred when creating the mailbox")
                        .build());
            }
        } catch (MailboxNameException | MailboxParentNotFoundException e) {
            builder.notCreated(mailboxCreationId, SetError.builder()
                    .type("invalidArguments")
                    .description(e.getMessage())
                    .build());
        } catch (MailboxExistsException e) {
            String message = String.format("The mailbox '%s' already exists.", mailboxCreationId.getCreationId());
            builder.notCreated(mailboxCreationId, SetError.builder()
                    .type("invalidArguments")
                    .description(message)
                    .build());
        } catch (MailboxException e) {
            String message = String.format("An error occurred when creating the mailbox '%s'", mailboxCreationId.getCreationId());
            LOGGER.error(message, e);
            builder.notCreated(mailboxCreationId, SetError.builder()
                    .type("anErrorOccurred")
                    .description(message)
                    .build());
        }
    }

    private void ensureValidMailboxName(MailboxRequest mailboxRequest, MailboxSession mailboxSession) {
        String name = mailboxRequest.getName();
        char pathDelimiter = mailboxSession.getPathDelimiter();
        if (name.contains(String.valueOf(pathDelimiter))) {
            throw new MailboxNameException(String.format("The mailbox '%s' contains an illegal character: '%c'", name, pathDelimiter));
        }
    }

    private MailboxPath getMailboxPath(MailboxRequest mailboxRequest, Map<MailboxCreationId, String> creationIdsToCreatedMailboxId, MailboxSession mailboxSession) throws MailboxException {
        if (mailboxRequest.getParentId().isPresent()) {
            String parentId = mailboxRequest.getParentId().get();
            String parentName = mailboxUtils.getMailboxNameFromId(parentId, mailboxSession)
                    .orElseGet(Throwing.supplier(() ->
                        mailboxUtils.getMailboxNameFromId(creationIdsToCreatedMailboxId.get(MailboxCreationId.of(parentId)), mailboxSession)
                            .orElseThrow(() -> new MailboxParentNotFoundException(parentId))
                    ));

            return new MailboxPath(mailboxSession.getPersonalSpace(), mailboxSession.getUser().getUserName(), 
                    parentName + mailboxSession.getPathDelimiter() + mailboxRequest.getName());
        }
        return new MailboxPath(mailboxSession.getPersonalSpace(), mailboxSession.getUser().getUserName(), mailboxRequest.getName());
    }
}
