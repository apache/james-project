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

import static org.apache.james.jmap.methods.Method.JMAP_PREFIX;

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.jmap.exceptions.MailboxHasChildException;
import org.apache.james.jmap.exceptions.MailboxParentNotFoundException;
import org.apache.james.jmap.exceptions.SystemMailboxNotUpdatableException;
import org.apache.james.jmap.model.MailboxFactory;
import org.apache.james.jmap.model.SetError;
import org.apache.james.jmap.model.SetMailboxesRequest;
import org.apache.james.jmap.model.SetMailboxesResponse;
import org.apache.james.jmap.model.SetMailboxesResponse.Builder;
import org.apache.james.jmap.model.mailbox.Mailbox;
import org.apache.james.jmap.model.mailbox.MailboxUpdateRequest;
import org.apache.james.jmap.model.mailbox.Role;
import org.apache.james.jmap.utils.MailboxUtils;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNameException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.TooLongMailboxNameException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.functions.ThrowingFunction;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

public class SetMailboxesUpdateProcessor implements SetMailboxesProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SetMailboxesUpdateProcessor.class);
    private final MailboxUtils mailboxUtils;
    private final MailboxManager mailboxManager;
    private final MailboxFactory mailboxFactory;
    private final MetricFactory metricFactory;
    private final SubscriptionManager subscriptionManager;

    @Inject
    @VisibleForTesting
    SetMailboxesUpdateProcessor(MailboxUtils mailboxUtils, MailboxManager mailboxManager, SubscriptionManager subscriptionManager, MailboxFactory mailboxFactory, MetricFactory metricFactory) {
        this.mailboxUtils = mailboxUtils;
        this.mailboxManager = mailboxManager;
        this.subscriptionManager = subscriptionManager;
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
            checkRole(mailbox.getRole());
            validateParent(mailbox, updateRequest, mailboxSession);

            updateMailbox(mailbox, updateRequest, mailboxSession);
            responseBuilder.updated(mailboxId);

        } catch (SystemMailboxNotUpdatableException e) {
            responseBuilder.notUpdated(mailboxId, SetError.builder()
                    .type("invalidArguments")
                    .description("Cannot update a system mailbox.")
                    .build());
        } catch (TooLongMailboxNameException e) {
            responseBuilder.notUpdated(mailboxId, SetError.builder()
                .type("invalidArguments")
                .description("The mailbox name length is too long")
                .build());
        } catch (MailboxNameException e) {
            responseBuilder.notUpdated(mailboxId, SetError.builder()
                    .type("invalidArguments")
                    .description(e.getMessage())
                    .build());
        } catch (MailboxNotFoundException e) {
            responseBuilder.notUpdated(mailboxId, SetError.builder()
                    .type("notFound")
                    .description(String.format("The mailbox '%s' was not found", mailboxId.serialize()))
                    .build());
        } catch (MailboxParentNotFoundException e) {
            responseBuilder.notUpdated(mailboxId, SetError.builder()
                    .type("notFound")
                    .description(String.format("The parent mailbox '%s' was not found.", e.getParentId()))
                    .build());
        } catch (MailboxHasChildException e) {
            responseBuilder.notUpdated(mailboxId, SetError.builder()
                    .type("invalidArguments")
                    .description("Cannot update a parent mailbox.")
                    .build());
        } catch (MailboxExistsException e) {
            responseBuilder.notUpdated(mailboxId, SetError.builder()
                    .type("invalidArguments")
                    .description("Cannot rename a mailbox to an already existing mailbox.")
                    .build());
        } catch (MailboxException e) {
            LOGGER.error("Error while updating mailbox", e);
            responseBuilder.notUpdated(mailboxId, SetError.builder()
                    .type( "anErrorOccurred")
                    .description("An error occurred when updating the mailbox")
                    .build());
        }
   }

    private void checkRole(Optional<Role> role) throws SystemMailboxNotUpdatableException {
        if (role.map(Role::isSystemRole).orElse(false)) {
            throw new SystemMailboxNotUpdatableException();
        }
    }

    private Mailbox getMailbox(MailboxId mailboxId, MailboxSession mailboxSession) throws MailboxNotFoundException {
        return mailboxFactory.builder()
                .id(mailboxId)
                .session(mailboxSession)
                .build()
                .orElseThrow(() -> new MailboxNotFoundException(mailboxId.serialize()));
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
                .isPresent() ;
    }

    private void validateParent(Mailbox mailbox, MailboxUpdateRequest updateRequest, MailboxSession mailboxSession) throws MailboxException, MailboxHasChildException {

        if (isParentIdInRequest(updateRequest)) {
            MailboxId newParentId = updateRequest.getParentId().get();
            try {
                mailboxManager.getMailbox(newParentId, mailboxSession);
            } catch (MailboxNotFoundException e) {
                throw new MailboxParentNotFoundException(newParentId);
            }
            if (mustChangeParent(mailbox.getParentId(), newParentId) && mailboxUtils.hasChildren(mailbox.getId(), mailboxSession)) {
                throw new MailboxHasChildException();
            }
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
            mailboxManager.setRights(originMailboxPath,
                updateRequest.getSharedWith()
                    .get()
                    .toMailboxAcl(),
                mailboxSession);
        }
        if (!originMailboxPath.equals(destinationMailboxPath)) {
            mailboxManager.renameMailbox(originMailboxPath, destinationMailboxPath, mailboxSession);

            subscriptionManager.unsubscribe(mailboxSession, originMailboxPath.getName());
            subscriptionManager.subscribe(mailboxSession, destinationMailboxPath.getName());
        }
    }

    private MailboxPath computeNewMailboxPath(Mailbox mailbox, MailboxPath originMailboxPath, MailboxUpdateRequest updateRequest, MailboxSession mailboxSession) throws MailboxException {
        Optional<MailboxId> parentId = updateRequest.getParentId();
        if (parentId == null) {
            return new MailboxPath(mailboxSession.getPersonalSpace(), 
                    mailboxSession.getUser().getUserName(), 
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
