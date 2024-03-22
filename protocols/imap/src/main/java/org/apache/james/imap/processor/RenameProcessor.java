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

package org.apache.james.imap.processor;

import static org.apache.james.util.ReactorUtils.logAsMono;

import jakarta.inject.Inject;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.RenameRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.TooLongMailboxNameException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

public class RenameProcessor extends AbstractMailboxProcessor<RenameRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenameProcessor.class);

    @Inject
    public RenameProcessor(MailboxManager mailboxManager, StatusResponseFactory factory,
                           MetricFactory metricFactory) {
        super(RenameRequest.class, mailboxManager, factory, metricFactory);
    }

    @Override
    protected Mono<Void> processRequestReactive(RenameRequest request, ImapSession session, Responder responder) {
        try {
            PathConverter pathConverter = PathConverter.forSession(session);
            MailboxPath existingPath = pathConverter.buildFullPath(request.getExistingName());
            MailboxPath newPath = pathConverter.buildFullPath(request.getNewName());
            MailboxManager mailboxManager = getMailboxManager();
            MailboxSession mailboxsession = session.getMailboxSession();

            return Mono.from(mailboxManager.renameMailboxReactive(existingPath, newPath, MailboxManager.RenameOption.NONE, mailboxsession))
                .then(createInboxIfNeeded(existingPath, mailboxsession))
                .then(Mono.fromRunnable(() -> okComplete(request, responder)))
                .onErrorResume(MailboxExistsException.class, e -> {
                    no(request, responder, HumanReadableText.MAILBOX_EXISTS);
                    return logAsMono(() -> LOGGER.debug("Rename from {} to {} failed because the target mailbox exists", existingPath, newPath, e));
                })
                .onErrorResume(MailboxNotFoundException.class, e -> {
                    no(request, responder, HumanReadableText.MAILBOX_NOT_FOUND);
                    return logAsMono(() -> LOGGER.debug("Rename from {} to {} failed because the source mailbox doesn't exist", existingPath, newPath, e));
                })
                .onErrorResume(TooLongMailboxNameException.class, e -> {
                    taggedBad(request, responder, HumanReadableText.FAILURE_MAILBOX_NAME);
                    return logAsMono(() -> LOGGER.debug("The mailbox name length is over limit: {}", newPath.getName(), e));
                })
                .onErrorResume(TooLongMailboxNameException.class, e -> {
                    no(request, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
                    return logAsMono(() -> LOGGER.error("Rename from {} to {} failed", existingPath, newPath, e));
                })
                .then(unsolicitedResponses(session, responder, false));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    private Mono<Void> createInboxIfNeeded(MailboxPath existingPath, MailboxSession session) {
        if (!existingPath.getName().equalsIgnoreCase(ImapConstants.INBOX_NAME)) {
            return Mono.empty();
        }
        return Mono.from(getMailboxManager().mailboxExists(existingPath, session))
            .flatMap(exisits -> {
                if (exisits) {
                    return Mono.empty();
                }
                return Mono.from(getMailboxManager().createMailboxReactive(existingPath, session));
            }).then();
    }

    @Override
    protected MDCBuilder mdc(RenameRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "RENAME")
            .addToContext("existingName", request.getExistingName())
            .addToContext("newName", request.getNewName());
    }
}
