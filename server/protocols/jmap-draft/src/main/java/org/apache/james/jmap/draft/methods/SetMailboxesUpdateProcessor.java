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

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.jmap.draft.exceptions.MailboxHasChildException;
import org.apache.james.jmap.draft.exceptions.MailboxNotOwnedException;
import org.apache.james.jmap.draft.exceptions.MailboxParentNotFoundException;
import org.apache.james.jmap.draft.exceptions.SystemMailboxNotUpdatableException;
import org.apache.james.jmap.draft.model.MailboxFactory;
import org.apache.james.jmap.draft.model.SetError;
import org.apache.james.jmap.draft.model.SetMailboxesRequest;
import org.apache.james.jmap.draft.model.SetMailboxesResponse;
import org.apache.james.jmap.draft.model.SetMailboxesResponse.Builder;
import org.apache.james.jmap.draft.model.mailbox.Mailbox;
import org.apache.james.jmap.draft.model.mailbox.MailboxUpdateRequest;
import org.apache.james.jmap.draft.utils.MailboxUtils;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.exception.DifferentDomainException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNameException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.TooLongMailboxNameException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.util.OptionalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.functions.ThrowingFunction;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

public class SetMailboxesUpdateProcessor implements SetMailboxesProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetMailboxesUpdateProcessor.class);
    private final MailboxUtils mailboxUtils;
    private final MailboxManager mailboxManager;
    private final MailboxFactory mailboxFactory;
    private final MetricFactory metricFactory;

    @Inject
    @VisibleForTesting
    SetMailboxesUpdateProcessor(MailboxUtils mailboxUtils, MailboxManager mailboxManager, MailboxFactory mailboxFactory, MetricFactory metricFactory) {
        this.mailboxUtils = mailboxUtils;
        this.mailboxManager = mailboxManager;
        this.mailboxFactory = mailboxFactory;
        this.metricFactory = metricFactory;
    }

    @Override
    public SetMailboxesResponse process(SetMailboxesRequest request, MailboxSession mailboxSession) {
        TimeMetric timeMetric = metricFactory.timer(JMAP_PREFIX + "mailboxUpdateProcessor");

        SetMailboxesResponse.Builder responseBuilder = SetMailboxesResponse.builder();
        request.getUpdate()
            .forEach((key, value) -> handleUpdate(key, value, responseBuilder, mailboxSession));
        timeMetric.stopAndPublish();
        return responseBuilder.build();
    }

    private void handleUpdate(MailboxId mailboxId, MailboxUpdateRequest updateRequest, Builder responseBuilder, MailboxSession mailboxSession) {
        try {
            validateMailboxName(updateRequest, mailboxSession);
            Mailbox mailbox = getMailbox(mailboxId, mailboxSession);
            assertNotSharedOutboxOrDraftMailbox(mailbox, updateRequest);
            assertSystemMailboxesAreNotUpdated(mailbox, updateRequest);
            validateParent(mailbox, updateRequest, mailboxSession);

            updateMailbox(mailbox, updateRequest, mailboxSession);
            responseBuilder.updated(mailboxId);

        } catch (SystemMailboxNotUpdatableException e) {
            responseBuilder.notUpdated(mailboxId, SetError.builder()
                    .type(SetError.Type.INVALID_ARGUMENTS)
                    .description("Cannot update a system mailbox.")
                    .build());
        } catch (TooLongMailboxNameException e) {
            responseBuilder.notUpdated(mailboxId, SetError.builder()
                .type(SetError.Type.INVALID_ARGUMENTS)
                .description("The mailbox name length is too long")
                .build());
        } catch (MailboxNameException | IllegalArgumentException e) {
            responseBuilder.notUpdated(mailboxId, SetError.builder()
                    .type(SetError.Type.INVALID_ARGUMENTS)
                    .description(e.getMessage())
                    .build());
        } catch (MailboxNotFoundException e) {
            responseBuilder.notUpdated(mailboxId, SetError.builder()
                    .type(SetError.Type.NOT_FOUND)
                    .description(String.format("The mailbox '%s' was not found", mailboxId.serialize()))
                    .build());
        } catch (MailboxParentNotFoundException e) {
            responseBuilder.notUpdated(mailboxId, SetError.builder()
                    .type(SetError.Type.NOT_FOUND)
                    .description(String.format("The parent mailbox '%s' was not found.", e.getParentId()))
                    .build());
        } catch (MailboxHasChildException e) {
            responseBuilder.notUpdated(mailboxId, SetError.builder()
                    .type(SetError.Type.INVALID_ARGUMENTS)
                    .description("Cannot update a parent mailbox.")
                    .build());
        } catch (MailboxNotOwnedException e) {
            responseBuilder.notUpdated(mailboxId, SetError.builder()
                    .type(SetError.Type.INVALID_ARGUMENTS)
                    .description("Parent mailbox is not owned.")
                    .build());
        } catch (MailboxExistsException e) {
            responseBuilder.notUpdated(mailboxId, SetError.builder()
                    .type(SetError.Type.INVALID_ARGUMENTS)
                    .description("Cannot rename a mailbox to an already existing mailbox.")
                    .build());
        } catch (DifferentDomainException e) {
            responseBuilder.notUpdated(mailboxId, SetError.builder()
                .type(SetError.Type.INVALID_ARGUMENTS)
                .description("Cannot share a mailbox to another domain")
                .build());
        } catch (MailboxException e) {
            LOGGER.error("Error while updating mailbox", e);
            responseBuilder.notUpdated(mailboxId, SetError.builder()
                    .type(SetError.Type.ERROR)
                    .description("An error occurred when updating the mailbox")
                    .build());
        }
    }

    private void assertNotSharedOutboxOrDraftMailbox(Mailbox mailbox, MailboxUpdateRequest updateRequest) {
        Preconditions.checkArgument(!updateRequest.getSharedWith().isPresent() || !mailbox.hasRole(Role.OUTBOX), "Sharing 'Outbox' is forbidden");
        Preconditions.checkArgument(!updateRequest.getSharedWith().isPresent() || !mailbox.hasRole(Role.DRAFTS), "Sharing 'Draft' is forbidden");
    }

    private void assertSystemMailboxesAreNotUpdated(Mailbox mailbox, MailboxUpdateRequest updateRequest) throws SystemMailboxNotUpdatableException {
        if (mailbox.hasSystemRole()) {
            if (OptionalUtils.containsDifferent(updateRequest.getName(), mailbox.getName())
                || requestChanged(updateRequest.getParentId(), mailbox.getParentId())
                || requestChanged(updateRequest.getRole(), mailbox.getRole())
                || OptionalUtils.containsDifferent(updateRequest.getSortOrder(), mailbox.getSortOrder())) {
                throw new SystemMailboxNotUpdatableException();
            }
        }
    }

    @VisibleForTesting
    <T> boolean requestChanged(Optional<T> requestValue, Optional<T> storeValue) {
        return requestValue.isPresent() && !requestValue.equals(storeValue);
    }

    private Mailbox getMailbox(MailboxId mailboxId, MailboxSession mailboxSession) throws MailboxNotFoundException {
        return mailboxFactory.builder()
                .id(mailboxId)
                .session(mailboxSession)
                .build()
                .blockOptional()
                .orElseThrow(() -> new MailboxNotFoundException(mailboxId));
    }

    private void validateMailboxName(MailboxUpdateRequest updateRequest, MailboxSession mailboxSession) throws MailboxNameException {
        char pathDelimiter = mailboxSession.getPathDelimiter();

        if (nameContainsPathDelimiter(updateRequest, pathDelimiter)) {
            throw new MailboxNameException(String.format("The mailbox '%s' contains an illegal character: '%c'", updateRequest.getName().get(), pathDelimiter));
        }
        if (nameMatchesSystemMailbox(updateRequest)) {
            throw new MailboxNameException(String.format("The mailbox '%s' is a system mailbox.", updateRequest.getName().get()));
        }
    }

    private boolean nameMatchesSystemMailbox(MailboxUpdateRequest updateRequest) {
        return updateRequest.getName()
                .flatMap(Role::from)
                .filter(Role::isSystemRole)
                .isPresent();
    }

    private boolean nameContainsPathDelimiter(MailboxUpdateRequest updateRequest, char pathDelimiter) {
        return updateRequest.getName()
                .filter(name -> name.contains(String.valueOf(pathDelimiter)))
                .isPresent();
    }

    private void validateParent(Mailbox mailbox, MailboxUpdateRequest updateRequest, MailboxSession mailboxSession) throws MailboxException, MailboxHasChildException {
        if (isParentIdInRequest(updateRequest)) {
            MailboxId newParentId = updateRequest.getParentId().get();
            MessageManager parent = retrieveParent(mailboxSession, newParentId);
            if (mustChangeParent(mailbox.getParentId(), newParentId)) {
                assertNoChildren(mailbox, mailboxSession);
                assertOwned(mailboxSession, parent);
            }
        }
    }

    private void assertNoChildren(Mailbox mailbox, MailboxSession mailboxSession) throws MailboxException, MailboxHasChildException {
        if (mailboxUtils.hasChildren(mailbox.getId(), mailboxSession)) {
            throw new MailboxHasChildException();
        }
    }

    private void assertOwned(MailboxSession mailboxSession, MessageManager parent) throws MailboxException {
        if (!parent.getMailboxPath().belongsTo(mailboxSession)) {
            throw new MailboxNotOwnedException();
        }
    }

    private MessageManager retrieveParent(MailboxSession mailboxSession, MailboxId newParentId) throws MailboxException {
        try {
            return mailboxManager.getMailbox(newParentId, mailboxSession);
        } catch (MailboxNotFoundException e) {
            throw new MailboxParentNotFoundException(newParentId);
        }
    }

    private boolean isParentIdInRequest(MailboxUpdateRequest updateRequest) {
        return updateRequest.getParentId() != null
                && updateRequest.getParentId().isPresent();
    }

    private boolean mustChangeParent(Optional<MailboxId> currentParentId, MailboxId newParentId) {
        return currentParentId
                .map(x -> ! x.equals(newParentId))
                .orElse(true);
    }

    private void updateMailbox(Mailbox mailbox, MailboxUpdateRequest updateRequest, MailboxSession mailboxSession) throws MailboxException {
        MailboxPath originMailboxPath = mailboxManager.getMailbox(mailbox.getId(), mailboxSession).getMailboxPath();
        MailboxPath destinationMailboxPath = computeNewMailboxPath(mailbox, originMailboxPath, updateRequest, mailboxSession);

        if (updateRequest.getSharedWith().isPresent()) {
            mailboxManager.setRights(mailbox.getId(),
                updateRequest.getSharedWith()
                    .get()
                    .removeEntriesFor(originMailboxPath.getUser())
                    .toMailboxAcl(),
                mailboxSession);
        }

        if (!originMailboxPath.equals(destinationMailboxPath)) {
            mailboxManager.renameMailbox(mailbox.getId(), destinationMailboxPath, MailboxManager.RenameOption.RENAME_SUBSCRIPTIONS, mailboxSession);
        }
    }

    private MailboxPath computeNewMailboxPath(Mailbox mailbox, MailboxPath originMailboxPath, MailboxUpdateRequest updateRequest, MailboxSession mailboxSession) throws MailboxException {
        Optional<MailboxId> parentId = updateRequest.getParentId();
        if (parentId == null) {
            return MailboxPath.forUser(
                mailboxSession.getUser(),
                updateRequest.getName().orElse(mailbox.getName()));
        }

        MailboxPath modifiedMailboxPath = updateRequest.getName()
                .map(newName -> computeMailboxPathWithNewName(originMailboxPath, newName))
                .orElse(originMailboxPath);
        ThrowingFunction<MailboxId, MailboxPath> computeNewMailboxPath = parentMailboxId -> computeMailboxPathWithNewParentId(modifiedMailboxPath, parentMailboxId, mailboxSession);
        return parentId
                .map(Throwing.function(computeNewMailboxPath).sneakyThrow())
                .orElse(modifiedMailboxPath);
    }

    private MailboxPath computeMailboxPathWithNewName(MailboxPath originMailboxPath, String newName) {
        return new MailboxPath(originMailboxPath, newName);
    }

    private MailboxPath computeMailboxPathWithNewParentId(MailboxPath originMailboxPath, MailboxId parentMailboxId, MailboxSession mailboxSession) throws MailboxException {
        MailboxPath newParentMailboxPath = mailboxManager.getMailbox(parentMailboxId, mailboxSession).getMailboxPath();
        String lastName = getCurrentMailboxName(originMailboxPath, mailboxSession);
        return new MailboxPath(originMailboxPath, newParentMailboxPath.getName() + mailboxSession.getPathDelimiter() + lastName);
    }

    private String getCurrentMailboxName(MailboxPath originMailboxPath, MailboxSession mailboxSession) {
        return Iterables.getLast(
                Splitter.on(mailboxSession.getPathDelimiter())
                    .splitToList(originMailboxPath.getName()));
    }

}
