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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.request.ImapRequest;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.EnableRequest;
import org.apache.james.imap.message.response.EnableResponse;
import org.apache.james.imap.processor.PermitEnableCapabilityProcessor.EnableException;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.MetricFactory;

public class EnableProcessor extends AbstractMailboxProcessor<EnableRequest> implements CapabilityImplementingProcessor {

    private final static List<PermitEnableCapabilityProcessor> capabilities = new ArrayList<>();
    public final static String ENABLED_CAPABILITIES = "ENABLED_CAPABILITIES";
    private final static List<String> CAPS = Collections.unmodifiableList(Arrays.asList(SUPPORTS_ENABLE));
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


    /**
     * @see org.apache.james.imap.processor.AbstractMailboxProcessor
     * #doProcess(org.apache.james.imap.api.message.request.ImapRequest,
     * org.apache.james.imap.api.process.ImapSession, java.lang.String,
     * org.apache.james.imap.api.ImapCommand, org.apache.james.imap.api.process.ImapProcessor.Responder)
     */
    protected void doProcess(EnableRequest request, ImapSession session, String tag, ImapCommand command, Responder responder) {
        try {

            List<String> caps = request.getCapabilities();
            Set<String> enabledCaps = enable(request, responder, session, caps.iterator());
            responder.respond(new EnableResponse(enabledCaps));

            unsolicitedResponses(session, responder, false);
            okComplete(command, tag, responder);
        } catch (EnableException e) {
            if (session.getLog().isInfoEnabled()) {
                session.getLog().info("Unable to enable extension", e);
            }
            taggedBad(command, tag, responder, HumanReadableText.FAILED);
        }
    }
   
    public Set<String> enable(ImapRequest request, Responder responder, ImapSession session, Iterator<String> caps) throws EnableException {
        Set<String> enabledCaps = new HashSet<>();
        while(caps.hasNext()) {
            String cap = caps.next();
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
     * 
     * @param implementor
     */
    public void addProcessor(PermitEnableCapabilityProcessor implementor) {
        capabilities.add(implementor);
    }

    /**
     * Return all enabled <code>CAPABILITIES</code> for this {@link ImapSession}
     * 
     * @param session
     * @return enabled
     */
    @SuppressWarnings("unchecked")
    public static Set<String> getEnabledCapabilities(ImapSession session) {
        Set<String> caps = (Set<String>) session.getAttribute(ENABLED_CAPABILITIES);
        
        if (caps == null) {
            caps = new HashSet<>();
            session.setAttribute(ENABLED_CAPABILITIES, caps);
        } 
        return caps;
    }
    
    /**
     * @see org.apache.james.imap.processor.CapabilityImplementingProcessor
     * #getImplementedCapabilities(org.apache.james.imap.api.process.ImapSession)
     */
    public List<String> getImplementedCapabilities(ImapSession session) {
        return CAPS;
    }

}
