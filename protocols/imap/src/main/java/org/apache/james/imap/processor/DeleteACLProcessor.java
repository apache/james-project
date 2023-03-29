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

import static org.apache.james.util.ReactorUtils.logOnError;

import java.util.List;

import javax.inject.Inject;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.DeleteACLRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.FunctionalUtils;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

/**
 * DELETEACL Processor.
 */
public class DeleteACLProcessor extends AbstractMailboxProcessor<DeleteACLRequest> implements CapabilityImplementingProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteACLProcessor.class);

    private static final List<Capability> CAPABILITIES = ImmutableList.of(ImapConstants.SUPPORTS_ACL);

    @Inject
    public DeleteACLProcessor(MailboxManager mailboxManager, StatusResponseFactory factory, MetricFactory metricFactory) {
        super(DeleteACLRequest.class, mailboxManager, factory, metricFactory);
    }

    @Override
    protected Mono<Void> processRequestReactive(DeleteACLRequest request, ImapSession session, Responder responder) {
        final MailboxManager mailboxManager = getMailboxManager();
        final MailboxSession mailboxSession = session.getMailboxSession();
        final String mailboxName = request.getMailboxName();
        final String identifier = request.getIdentifier();
        MailboxPath mailboxPath = PathConverter.forSession(session).buildFullPath(mailboxName);

        return checkLookupRight(request, responder, mailboxManager, mailboxSession, mailboxPath)
            .filter(FunctionalUtils.identityPredicate())
            .flatMap(hasLookupRight -> checkAdminRight(request, responder, mailboxManager, mailboxSession, mailboxName, mailboxPath))
            .filter(FunctionalUtils.identityPredicate())
            .flatMap(hasAdminRight -> applyRight(mailboxManager, mailboxSession, identifier, mailboxPath)
                .then(Mono.fromRunnable(() -> okComplete(request, responder)))
                .then())
            .onErrorResume(UnsupportedRightException.class,
                error -> Mono.fromRunnable(() -> taggedBad(request, responder,
                    new HumanReadableText(
                        HumanReadableText.UNSUPPORTED_RIGHT_KEY,
                        HumanReadableText.UNSUPPORTED_RIGHT_DEFAULT_VALUE,
                        error.getUnsupportedRight()))))
            .onErrorResume(MailboxNotFoundException.class,
                error -> Mono.fromRunnable(() -> no(request, responder, HumanReadableText.MAILBOX_NOT_FOUND)))
            .doOnEach(logOnError(MailboxException.class, e -> LOGGER.error("{} failed for mailbox {}", request.getCommand().getName(), mailboxName, e)))
            .onErrorResume(MailboxException.class,
                error -> Mono.fromRunnable(() -> no(request, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING)));
    }

    private static Mono<Void> applyRight(MailboxManager mailboxManager, MailboxSession mailboxSession, String identifier, MailboxPath mailboxPath) {
        return Mono.from(mailboxManager.applyRightsCommandReactive(
            mailboxPath,
            MailboxACL.command().key(EntryKey.deserialize(identifier)).noRights().asReplacement(),
            mailboxSession));
    }

    private Mono<Boolean> checkAdminRight(DeleteACLRequest request, Responder responder, MailboxManager mailboxManager, MailboxSession mailboxSession, String mailboxName, MailboxPath mailboxPath) {
        return Mono.from(mailboxManager.hasRightReactive(mailboxPath, MailboxACL.Right.Administer, mailboxSession))
            .doOnNext(hasRight -> {
                if (!hasRight) {
                    no(request, responder,
                        new HumanReadableText(
                            HumanReadableText.UNSUFFICIENT_RIGHTS_KEY,
                            HumanReadableText.UNSUFFICIENT_RIGHTS_DEFAULT_VALUE,
                            MailboxACL.Right.Administer.toString(),
                            request.getCommand().getName(),
                            mailboxName));
                }
            });
    }

    private Mono<Boolean> checkLookupRight(DeleteACLRequest request, Responder responder, MailboxManager mailboxManager, MailboxSession mailboxSession, MailboxPath mailboxPath) {
        return Mono.from(mailboxManager.hasRightReactive(mailboxPath, MailboxACL.Right.Lookup, mailboxSession))
            .doOnNext(hasRight -> {
                if (!hasRight) {
                    no(request, responder, HumanReadableText.MAILBOX_NOT_FOUND);
                }
            });
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        return CAPABILITIES;
    }

    @Override
    protected MDCBuilder mdc(DeleteACLRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "DELETE_ACL")
            .addToContext("mailbox", request.getMailboxName())
            .addToContext("identifier", request.getIdentifier());
    }
}
