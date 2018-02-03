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

import java.io.Closeable;
import java.util.Collections;
import java.util.List;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.StartTLSRequest;
import org.apache.james.imap.processor.base.AbstractChainedProcessor;
import org.apache.james.util.MDCBuilder;

import com.google.common.collect.ImmutableList;

/**
 * Processing STARTLS commands
 */
public class StartTLSProcessor extends AbstractChainedProcessor<StartTLSRequest> implements CapabilityImplementingProcessor {
    private static final List<String> STARTTLS_CAP = ImmutableList.of(ImapConstants.SUPPORTS_STARTTLS);
    private final StatusResponseFactory factory;

    public StartTLSProcessor(ImapProcessor next, StatusResponseFactory factory) {
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

    @Override
    protected Closeable addContextToMDC(StartTLSRequest message) {
        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "START_TLS")
            .build();
    }
}
