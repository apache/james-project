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

import static org.apache.james.imap.api.ImapConstants.SUPPORTS_ENABLE;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.request.ImapRequest;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.EnableRequest;
import org.apache.james.imap.message.response.EnableResponse;
import org.apache.james.imap.processor.PermitEnableCapabilityProcessor.EnableException;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

public class EnableProcessor extends AbstractMailboxProcessor<EnableRequest> implements CapabilityImplementingProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnableProcessor.class);

    private static final List<PermitEnableCapabilityProcessor> capabilities = new ArrayList<>();
    public static final String ENABLED_CAPABILITIES = "ENABLED_CAPABILITIES";
    private static final List<Capability> CAPS = ImmutableList.of(SUPPORTS_ENABLE);
    private final CapabilityProcessor capabilityProcessor;

    public EnableProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory, List<PermitEnableCapabilityProcessor> capabilities,
            MetricFactory metricFactory, CapabilityProcessor capabilityProcessor) {
        this(next, mailboxManager, factory, metricFactory, capabilityProcessor);
        EnableProcessor.capabilities.addAll(capabilities);

    }

    public EnableProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory,
            MetricFactory metricFactory, CapabilityProcessor capabilityProcessor) {
        super(EnableRequest.class, next, mailboxManager, factory, metricFactory);
        this.capabilityProcessor = capabilityProcessor;
    }


    @Override
    protected void processRequest(EnableRequest request, ImapSession session, Responder responder) {
        try {

            List<Capability> caps = request.getCapabilities();
            Set<Capability> enabledCaps = enable(request, responder, session, caps.iterator());
            responder.respond(new EnableResponse(enabledCaps));

            unsolicitedResponses(session, responder, false);
            okComplete(request, responder);
        } catch (EnableException e) {
            LOGGER.info("Unable to enable extension", e);
            taggedBad(request, responder, HumanReadableText.FAILED);
        }
    }
   
    public Set<Capability> enable(ImapRequest request, Responder responder, ImapSession session, Iterator<Capability> caps) throws EnableException {
        Set<Capability> enabledCaps = new HashSet<>();
        while (caps.hasNext()) {
            Capability cap = caps.next();
            // Check if the CAPABILITY is supported at all
            if (capabilityProcessor.getSupportedCapabilities(session).contains(cap)) {
                for (PermitEnableCapabilityProcessor enableProcessor : capabilities) {
                    if (enableProcessor.getPermitEnableCapabilities(session).contains(cap)) {
                        enableProcessor.enable(request, responder, session, cap);
                        enabledCaps.add(cap);
                    }
                }
            }
        }
        getEnabledCapabilities(session).addAll(enabledCaps);
        return enabledCaps;
    }

    /**
     * Add a {@link PermitEnableCapabilityProcessor} which can be enabled
     */
    public void addProcessor(PermitEnableCapabilityProcessor implementor) {
        capabilities.add(implementor);
    }

    /**
     * Return all enabled <code>CAPABILITIES</code> for this {@link ImapSession}
     */
    @SuppressWarnings("unchecked")
    public static Set<Capability> getEnabledCapabilities(ImapSession session) {
        Set<Capability> caps = (Set<Capability>) session.getAttribute(ENABLED_CAPABILITIES);
        
        if (caps == null) {
            caps = new HashSet<>();
            session.setAttribute(ENABLED_CAPABILITIES, caps);
        } 
        return caps;
    }
    
    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        return CAPS;
    }

    @Override
    protected Closeable addContextToMDC(EnableRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "ENABLE")
            .addToContext("capabilities", ImmutableList.copyOf(request.getCapabilities()).toString())
            .build();
    }
}
