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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.StartTLSRequest;
import org.apache.james.imap.processor.base.AbstractChainedProcessor;

/**
 * Processing STARTLS commands
 */
public class StartTLSProcessor extends AbstractChainedProcessor<StartTLSRequest> implements CapabilityImplementingProcessor {
    private final static List<String> STARTTLS_CAP = Collections.unmodifiableList(Arrays.asList(ImapConstants.SUPPORTS_STARTTLS));
    private StatusResponseFactory factory;

    public StartTLSProcessor(final ImapProcessor next, final StatusResponseFactory factory) {
        super(StartTLSRequest.class, next);
        this.factory = factory;
    }

    /**
     * @see
     * org.apache.james.imap.processor.base.AbstractChainedProcessor
     * #doProcess(org.apache.james.imap.api.ImapMessage,
     * org.apache.james.imap.api.process.ImapProcessor.Responder,
     * org.apache.james.imap.api.process.ImapSession)
     */
    protected void doProcess(StartTLSRequest request, Responder responder, ImapSession session) {
        if (session.supportStartTLS()) {
            responder.respond(factory.taggedOk(request.getTag(), request.getCommand(), HumanReadableText.STARTTLS));
            session.startTLS();

        } else {
            responder.respond(factory.taggedBad(request.getTag(), request.getCommand(), HumanReadableText.UNKNOWN_COMMAND));
        }

    }

    /**
     * @see org.apache.james.imap.processor.CapabilityImplementingProcessor
     * #getImplementedCapabilities(org.apache.james.imap.api.process.ImapSession)
     */
    public List<String> getImplementedCapabilities(ImapSession session) {
        if (session.supportStartTLS()) {
            return STARTTLS_CAP;
        } else {
            return Collections.emptyList();
        }
    }

}
