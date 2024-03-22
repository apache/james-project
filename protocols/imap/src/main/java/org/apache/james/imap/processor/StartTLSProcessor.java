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
import org.apache.james.imap.message.request.StartTLSRequest;
import org.apache.james.imap.processor.base.AbstractProcessor;
import org.apache.james.util.MDCBuilder;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

/**
 * Processing STARTLS commands
 */
public class StartTLSProcessor extends AbstractProcessor<StartTLSRequest> implements CapabilityImplementingProcessor {
    private static final List<Capability> STARTTLS_CAP = ImmutableList.of(ImapConstants.SUPPORTS_STARTTLS);
    private final StatusResponseFactory factory;

    @Inject
    public StartTLSProcessor(StatusResponseFactory factory) {
        super(StartTLSRequest.class);
        this.factory = factory;
    }

    @Override
    protected Mono<Void> doProcess(StartTLSRequest request, Responder responder, ImapSession session) {
        return Mono.fromRunnable(() -> {
            if (session.supportStartTLS()) {
                session.startTLS(() -> {
                    responder.respond(factory.taggedOk(request.getTag(), request.getCommand(), HumanReadableText.STARTTLS));
                    responder.flush();
                });
            } else {
                responder.respond(factory.taggedBad(request.getTag(), request.getCommand(), HumanReadableText.UNKNOWN_COMMAND));
            }
        });
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        if (session.supportStartTLS()) {
            return STARTTLS_CAP;
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    protected MDCBuilder mdc(StartTLSRequest message) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "START_TLS");
    }
}
