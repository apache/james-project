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

    private final NamespaceSupplier namespaceSupplier;


    @Inject
    public NamespaceProcessor(MailboxManager mailboxManager, StatusResponseFactory factory, MetricFactory metricFactory, NamespaceSupplier namespaceSupplier) {
        super(NamespaceRequest.class, mailboxManager, factory, metricFactory);
        this.namespaceSupplier = namespaceSupplier;
    }

    @Override
    protected Mono<Void> processRequestReactive(NamespaceRequest request, ImapSession session, Responder responder) {
        MailboxSession mailboxSession = session.getMailboxSession();

        NamespaceResponse response = new NamespaceResponse(namespaceSupplier.personalNamespaces(mailboxSession),
            namespaceSupplier.otherUsersNamespaces(mailboxSession),
            namespaceSupplier.sharedNamespaces(mailboxSession));

        responder.respond(response);

        return unsolicitedResponses(session, responder, false)
            .then(Mono.fromRunnable(() -> okComplete(request, responder)));
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
