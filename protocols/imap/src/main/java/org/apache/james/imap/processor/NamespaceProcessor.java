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

import static org.apache.james.imap.api.ImapConstants.SUPPORTS_NAMESPACES;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.NamespaceRequest;
import org.apache.james.imap.message.response.NamespaceResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

/**
 * Processes a NAMESPACE command into a suitable set of responses.
 */
public class NamespaceProcessor extends AbstractMailboxProcessor<NamespaceRequest> implements CapabilityImplementingProcessor {
    private static final List<Capability> CAPS = ImmutableList.of(SUPPORTS_NAMESPACES);

    @Inject
    public NamespaceProcessor(MailboxManager mailboxManager, StatusResponseFactory factory, MetricFactory metricFactory) {
        super(NamespaceRequest.class, mailboxManager, factory, metricFactory);
    }

    @Override
    protected Mono<Void> processRequestReactive(NamespaceRequest request, ImapSession session, Responder responder) {
        final MailboxSession mailboxSession = session.getMailboxSession();
        final List<NamespaceResponse.Namespace> personalNamespaces = buildPersonalNamespaces(mailboxSession, session);
        final List<NamespaceResponse.Namespace> otherUsersNamespaces = buildOtherUsersSpaces(mailboxSession, session);
        final List<NamespaceResponse.Namespace> sharedNamespaces = buildSharedNamespaces(mailboxSession, session);
        final NamespaceResponse response = new NamespaceResponse(personalNamespaces, otherUsersNamespaces, sharedNamespaces);
        responder.respond(response);
        return unsolicitedResponses(session, responder, false)
            .then(Mono.fromRunnable(() -> okComplete(request, responder)));
    }

    /**
     * Builds personal namespaces from the session.
     * 
     * @param mailboxSession
     *            not null
     * @return personal namespaces, not null
     */
    private List<NamespaceResponse.Namespace> buildPersonalNamespaces(MailboxSession mailboxSession, ImapSession session) {
        final List<NamespaceResponse.Namespace> personalSpaces = new ArrayList<>();
        String personal = "";
        if (session.supportMultipleNamespaces()) {
            personal = mailboxSession.getPersonalSpace();
        }
        personalSpaces.add(new NamespaceResponse.Namespace(personal, mailboxSession.getPathDelimiter()));
        return personalSpaces;
    }

    private List<NamespaceResponse.Namespace> buildOtherUsersSpaces(MailboxSession mailboxSession,  ImapSession session) {
        final String otherUsersSpace = mailboxSession.getOtherUsersSpace();
        final List<NamespaceResponse.Namespace> otherUsersSpaces;
        if (session.supportMultipleNamespaces() == false || otherUsersSpace == null) {
            otherUsersSpaces = null;
        } else {
            otherUsersSpaces = new ArrayList<>(1);
            otherUsersSpaces.add(new NamespaceResponse.Namespace(otherUsersSpace, mailboxSession.getPathDelimiter()));
        }
        return otherUsersSpaces;
    }

    private List<NamespaceResponse.Namespace> buildSharedNamespaces(MailboxSession mailboxSession,  ImapSession session) {
        List<NamespaceResponse.Namespace> sharedNamespaces = null;
        final Collection<String> sharedSpaces = mailboxSession.getSharedSpaces();
        if (session.supportMultipleNamespaces() && !sharedSpaces.isEmpty()) {
            sharedNamespaces = new ArrayList<>(sharedSpaces.size());
            for (String space : sharedSpaces) {
                sharedNamespaces.add(new NamespaceResponse.Namespace(space, mailboxSession.getPathDelimiter()));
            }
        }
        return sharedNamespaces;
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        return CAPS;
    }

    @Override
    protected MDCBuilder mdc(NamespaceRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "NAMESPACE");
    }
}
