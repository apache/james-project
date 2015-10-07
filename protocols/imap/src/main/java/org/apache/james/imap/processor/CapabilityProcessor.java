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

import static org.apache.james.imap.api.ImapConstants.SUPPORTS_LITERAL_PLUS;
import static org.apache.james.imap.api.ImapConstants.SUPPORTS_RFC3348;
import static org.apache.james.imap.api.ImapConstants.VERSION;
import static org.apache.james.imap.api.ImapConstants.UTF8;
import static org.apache.james.imap.api.ImapConstants.SUPPORTS_I18NLEVEL_1;
import static org.apache.james.imap.api.ImapConstants.SUPPORTS_CONDSTORE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.display.CharsetUtil;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.CapabilityRequest;
import org.apache.james.imap.message.response.CapabilityResponse;
import org.apache.james.mailbox.MailboxManager;

public class CapabilityProcessor extends AbstractMailboxProcessor<CapabilityRequest> implements CapabilityImplementingProcessor {

    private final static List<String> CAPS;
    
    static {
        List<String> caps = new ArrayList<String>();
        caps.add(VERSION);
        caps.add(SUPPORTS_LITERAL_PLUS);
        caps.add(SUPPORTS_RFC3348);

        // UTF-8 is needed for I18NLEVEL_1
        if (CharsetUtil.getAvailableCharsetNames().contains(UTF8)) {
            caps.add(SUPPORTS_I18NLEVEL_1);
        }
        caps.add(SUPPORTS_CONDSTORE);
        CAPS = Collections.unmodifiableList(caps);
    }
    
    private static final List<CapabilityImplementingProcessor> capabilities = new ArrayList<CapabilityImplementingProcessor>();
    private static final Set<String> disabledCaps = new HashSet<String>();
    
    public CapabilityProcessor(final ImapProcessor next, final MailboxManager mailboxManager, final StatusResponseFactory factory, final List<CapabilityImplementingProcessor> capabilities, final Set<String> disabledCaps) {
        this(next, mailboxManager, factory, disabledCaps);
        CapabilityProcessor.capabilities.addAll(capabilities);

    }

    public CapabilityProcessor(final ImapProcessor next, final MailboxManager mailboxManager, final StatusResponseFactory factory,final Set<String> disabledCaps) {
        super(CapabilityRequest.class, next, mailboxManager, factory);
        CapabilityProcessor.disabledCaps.addAll(disabledCaps);
        capabilities.add(this); 
        
    }

    /**
     * @see
     * org.apache.james.imap.processor.AbstractMailboxProcessor#doProcess(org.apache.james.imap.api.message.request.ImapRequest,
     * org.apache.james.imap.api.process.ImapSession, java.lang.String,
     * org.apache.james.imap.api.ImapCommand,
     * org.apache.james.imap.api.process.ImapProcessor.Responder)
     */
    protected void doProcess(CapabilityRequest request, ImapSession session, String tag, ImapCommand command, Responder responder) {
        final CapabilityResponse result = new CapabilityResponse(getSupportedCapabilities(session));        
        responder.respond(result);
        unsolicitedResponses(session, responder, false);
        okComplete(command, tag, responder);
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

    /**
     * @see org.apache.james.imap.processor.CapabilityImplementingProcessor
     * #getImplementedCapabilities(org.apache.james.imap.api.process.ImapSession)
     */
    public List<String> getImplementedCapabilities(ImapSession session) {
        return CAPS;
    }
    
    /**
     * Return all supported <code>CAPABILITIES</code> for this {@link ImapSession}
     * 
     * @param session
     * @return supported
     */
    public static Set<String> getSupportedCapabilities(ImapSession session) {
        Set<String> caps = new HashSet<String>();
        for (int i = 0; i < capabilities.size(); i++) {
            caps.addAll(capabilities.get(i).getImplementedCapabilities(session));
        }
        caps.removeAll(disabledCaps);
        return caps;
    }
    

}
