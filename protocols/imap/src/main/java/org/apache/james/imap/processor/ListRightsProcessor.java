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

import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.ListRightsRequest;
import org.apache.james.imap.message.response.ListRightsResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

/**
 * LISTRIGHTS Processor.
 */
public class ListRightsProcessor extends AbstractMailboxProcessor<ListRightsRequest> implements CapabilityImplementingProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ListRightsProcessor.class);

    private static final List<Capability> CAPABILITIES = ImmutableList.of(ImapConstants.SUPPORTS_ACL);

    @Inject
    public ListRightsProcessor(MailboxManager mailboxManager, StatusResponseFactory factory,
                               MetricFactory metricFactory) {
        super(ListRightsRequest.class, mailboxManager, factory, metricFactory);
    }

    @Override
    protected Mono<Void> processRequestReactive(ListRightsRequest request, ImapSession session, Responder responder) {
        MailboxManager mailboxManager = getMailboxManager();
        MailboxSession mailboxSession = session.getMailboxSession();
        String mailboxName = request.getMailboxName();
        String identifier = request.getIdentifier();
        MailboxPath mailboxPath = PathConverter.forSession(session).buildFullPath(mailboxName);

        return Mono.from(mailboxManager.getMailboxReactive(mailboxPath, mailboxSession))
            .doOnNext(Throwing.<MessageManager>consumer(mailbox -> {
                /*
                 * RFC 4314 section 6.
                 * An implementation MUST make sure the ACL commands themselves do
                 * not give information about mailboxes with appropriately
                 * restricted ACLs. For example, when a user agent executes a GETACL
                 * command on a mailbox that the user has no permission to LIST, the
                 * server would respond to that request with the same error that
                 * would be used if the mailbox did not exist, thus revealing no
                 * existence information, much less the mailbox’s ACL.
                 */
                if (!mailboxManager.hasRight(mailbox.getMailboxEntity(), MailboxACL.Right.Lookup, mailboxSession)) {
                    no(request, responder, HumanReadableText.MAILBOX_NOT_FOUND);
                } else if (!mailboxManager.hasRight(mailbox.getMailboxEntity(), MailboxACL.Right.Administer, mailboxSession)) {
                    /* RFC 4314 section 4. */
                    Object[] params = new Object[] {
                        MailboxACL.Right.Administer.toString(),
                        request.getCommand().getName(),
                        mailboxName
                    };
                    HumanReadableText text = new HumanReadableText(HumanReadableText.UNSUFFICIENT_RIGHTS_KEY, HumanReadableText.UNSUFFICIENT_RIGHTS_DEFAULT_VALUE, params);
                    no(request, responder, text);
                } else {

                    EntryKey key = EntryKey.deserialize(identifier);

                    // FIXME check if identifier is a valid user or group
                    // FIXME Servers, when processing a command that has an identifier as a
                    // parameter (i.e., any of SETACL, DELETEACL, and LISTRIGHTS commands),
                    // SHOULD first prepare the received identifier using "SASLprep" profile
                    // [SASLprep] of the "stringprep" algorithm [Stringprep].  If the
                    // preparation of the identifier fails or results in an empty string,
                    // the server MUST refuse to perform the command with a BAD response.
                    // Note that Section 6 recommends additional identifier’s verification
                    // steps.

                    List<Rfc4314Rights> rights = mailboxManager.listRights(mailbox.getMailboxEntity(), key, mailboxSession);
                    ListRightsResponse aclResponse = new ListRightsResponse(mailboxName, identifier, rights);
                    responder.respond(aclResponse);
                    okComplete(request, responder);
                }
            }).sneakyThrow())
            .then()
            .onErrorResume(MailboxNotFoundException.class, e -> {
                no(request, responder, HumanReadableText.MAILBOX_NOT_FOUND);
                return Mono.empty();
            })
            .onErrorResume(MailboxException.class, e -> {
                no(request, responder, HumanReadableText.GENERIC_FAILURE_DURING_PROCESSING);
                return ReactorUtils.logAsMono(() -> LOGGER.error("{} failed for mailbox {}", request.getCommand().getName(), mailboxName, e));
            });
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        return CAPABILITIES;
    }

    @Override
    protected MDCBuilder mdc(ListRightsRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "LIST_RIGHTS")
            .addToContext("mailbox", request.getMailboxName())
            .addToContext("identifier", request.getIdentifier());
    }
}
