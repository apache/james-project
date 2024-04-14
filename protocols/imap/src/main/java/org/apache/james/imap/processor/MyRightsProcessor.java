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

import java.util.Collections;
import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.MyRightsRequest;
import org.apache.james.imap.message.response.MyRightsResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Mono;

/**
 * MYRIGHTS Processor.
 */
public class MyRightsProcessor extends AbstractMailboxProcessor<MyRightsRequest> implements CapabilityImplementingProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(MyRightsProcessor.class);

    private static final List<Capability> CAPABILITIES = Collections.singletonList(ImapConstants.SUPPORTS_ACL);

    @Inject
    public MyRightsProcessor(MailboxManager mailboxManager, StatusResponseFactory factory, MetricFactory metricFactory) {
        super(MyRightsRequest.class, mailboxManager, factory, metricFactory);
    }

    @Override
    protected Mono<Void> processRequestReactive(MyRightsRequest request, ImapSession session, Responder responder) {
        MailboxManager mailboxManager = getMailboxManager();
        MailboxSession mailboxSession = session.getMailboxSession();
        String mailboxName = request.getMailboxName();

        MailboxPath mailboxPath = PathConverter.forSession(session).buildFullPath(mailboxName);
        return Mono.from(mailboxManager.getMailboxReactive(mailboxPath, mailboxSession))
            .doOnNext(Throwing.consumer(mailbox -> {
                Rfc4314Rights myRights = mailboxManager.myRights(mailbox.getMailboxEntity(), mailboxSession);
                /*
                 * RFC 4314 section 6. An implementation MUST make sure the ACL
                 * commands themselves do not give information about mailboxes with
                 * appropriately restricted ACLs. For example, when a user agent
                 * executes a GETACL command on a mailbox that the user has no
                 * permission to LIST, the server would respond to that request with
                 * the same error that would be used if the mailbox did not exist,
                 * thus revealing no existence information, much less the mailbox’s
                 * ACL.
                 *
                 * RFC 4314 section 4. * MYRIGHTS - any of the following rights is
                 * required to perform the operation: "l", "r", "i", "k", "x", "a".
                 */
                if (!myRights.contains(MailboxACL.Right.Lookup)
                    && !myRights.contains(MailboxACL.Right.Read)
                    && !myRights.contains(MailboxACL.Right.Insert)
                    && !myRights.contains(MailboxACL.Right.CreateMailbox)
                    && !myRights.contains(MailboxACL.Right.DeleteMailbox)
                    && !myRights.contains(MailboxACL.Right.Administer)) {
                    no(request, responder, HumanReadableText.MAILBOX_NOT_FOUND);
                } else {
                    MyRightsResponse myRightsResponse = new MyRightsResponse(mailboxName, myRights);
                    responder.respond(myRightsResponse);
                    okComplete(request, responder);
                }
            }))
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
    protected MDCBuilder mdc(MyRightsRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "MYRIGHTS")
            .addToContext("mailbox", request.getMailboxName());
    }
}
