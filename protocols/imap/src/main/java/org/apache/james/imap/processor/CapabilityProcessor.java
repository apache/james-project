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

import static org.apache.james.imap.api.ImapConstants.BASIC_CAPABILITIES;
import static org.apache.james.imap.api.ImapConstants.SUPPORTS_CONDSTORE;
import static org.apache.james.imap.api.ImapConstants.SUPPORTS_I18NLEVEL_1;
import static org.apache.james.imap.api.ImapConstants.SUPPORTS_LITERAL_PLUS;
import static org.apache.james.imap.api.ImapConstants.SUPPORTS_OBJECTID;
import static org.apache.james.imap.api.ImapConstants.SUPPORTS_RFC3348;
import static org.apache.james.imap.api.ImapConstants.SUPPORTS_SAVEDATE;
import static org.apache.james.mailbox.MailboxManager.MessageCapabilities.UniqueID;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.imap.api.ImapConfiguration;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.CapabilityRequest;
import org.apache.james.imap.message.response.CapabilityResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

public class CapabilityProcessor extends AbstractMailboxProcessor<CapabilityRequest> implements CapabilityImplementingProcessor {

    private static final List<Capability> CAPS = ImmutableList.of(
        BASIC_CAPABILITIES,
        SUPPORTS_LITERAL_PLUS,
        SUPPORTS_RFC3348,
        SUPPORTS_I18NLEVEL_1,
        SUPPORTS_CONDSTORE,
        SUPPORTS_OBJECTID,
        SUPPORTS_SAVEDATE);

    private final List<CapabilityImplementingProcessor> capabilities = new ArrayList<>();
    private final Set<Capability> disabledCaps = new HashSet<>();

    @Inject
    public CapabilityProcessor(MailboxManager mailboxManager, StatusResponseFactory factory, MetricFactory metricFactory) {
        super(CapabilityRequest.class, mailboxManager, factory, metricFactory);
        capabilities.add(this);
    }

    @Override
    public void configure(ImapConfiguration imapConfiguration) {
        super.configure(imapConfiguration);

        disabledCaps.addAll(imapConfiguration.getDisabledCaps());
        if (imapConfiguration.isCondstoreEnable()) {
            disabledCaps.remove(SUPPORTS_CONDSTORE);
        } else {
            disabledCaps.add(SUPPORTS_CONDSTORE);
        }
        if (!getMailboxManager().getSupportedMessageCapabilities().contains(UniqueID)) {
            disabledCaps.add(SUPPORTS_OBJECTID);
        }
    }

    @Override
    protected Mono<Void> processRequestReactive(CapabilityRequest request, ImapSession session, Responder responder) {
        final CapabilityResponse result = new CapabilityResponse(getSupportedCapabilities(session));        
        responder.respond(result);
        return unsolicitedResponses(session, responder, false)
            .then(Mono.fromRunnable(() -> okComplete(request, responder)));
    }

    /**
     * Add a {@link CapabilityImplementingProcessor} which will get queried for
     * implemented capabilities
     * 
     * @param implementor
     */
    public void addProcessor(CapabilityImplementingProcessor implementor) {
        capabilities.add(implementor);
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        return CAPS;
    }
    
    /**
     * Return all supported <code>CAPABILITIES</code> for this {@link ImapSession}
     * 
     * @param session
     * @return supported
     */
    public Set<Capability> getSupportedCapabilities(ImapSession session) {
        Set<Capability> caps = new HashSet<>();
        for (CapabilityImplementingProcessor capability : capabilities) {
            caps.addAll(capability.getImplementedCapabilities(session));
        }
        caps.removeAll(disabledCaps);
        return caps;
    }

    @Override
    protected MDCBuilder mdc(CapabilityRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "CAPABILITY");
    }
    

}
