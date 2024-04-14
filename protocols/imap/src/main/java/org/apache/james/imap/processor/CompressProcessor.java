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
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.CompressRequest;
import org.apache.james.imap.processor.base.AbstractProcessor;
import org.apache.james.util.MDCBuilder;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

public class CompressProcessor extends AbstractProcessor<CompressRequest> implements CapabilityImplementingProcessor {
    private static final String ALGO = "DEFLATE";
    private static final List<Capability> CAPA = ImmutableList.of(Capability.of(ImapConstants.COMPRESS_COMMAND.getName() + "=" + ALGO));
    private final StatusResponseFactory factory;
    private static final String COMPRESSED = "COMPRESSED";

    @Inject
    public CompressProcessor(StatusResponseFactory factory) {
        super(CompressRequest.class);
        this.factory = factory;
    }

    @Override
    protected Mono<Void> doProcess(CompressRequest request, Responder responder, ImapSession session) {
        return Mono.fromRunnable(() -> {
            if (session.isCompressionSupported()) {
                Object obj = session.getAttribute(COMPRESSED);
                if (obj != null) {
                    responder.respond(factory.taggedNo(request.getTag(), request.getCommand(), HumanReadableText.COMPRESS_ALREADY_ACTIVE));
                } else {
                    if (!request.getAlgorithm().equalsIgnoreCase(ALGO)) {
                        responder.respond(factory.taggedBad(request.getTag(), request.getCommand(), HumanReadableText.ILLEGAL_ARGUMENTS));
                    } else {
                        StatusResponse response = factory.taggedOk(request.getTag(), request.getCommand(), HumanReadableText.DEFLATE_ACTIVE);

                        if (activateCompression(responder, session, response)) {
                            session.setAttribute(COMPRESSED, true);
                        }
                    }
                }
            } else {
                responder.respond(factory.taggedBad(request.getTag(), request.getCommand(), HumanReadableText.UNKNOWN_COMMAND));
            }
        });
    }

    private boolean activateCompression(Responder responder, ImapSession session, StatusResponse response) {
        return session.startCompression(() -> {
            responder.respond(response);
            responder.flush();
        });
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        if (session.isCompressionSupported()) {
            return CAPA;
        }
        return Collections.emptyList();
    }

    @Override
    protected MDCBuilder mdc(CompressRequest message) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "COMPRESS")
            .addToContext("algorithm", message.getAlgorithm());
    }
}
