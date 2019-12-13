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
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.CompressRequest;
import org.apache.james.imap.processor.base.AbstractChainedProcessor;
import org.apache.james.util.MDCBuilder;

import com.google.common.collect.ImmutableList;

public class CompressProcessor extends AbstractChainedProcessor<CompressRequest> implements CapabilityImplementingProcessor {
    private static final String ALGO = "DEFLATE";
    private static final List<Capability> CAPA = ImmutableList.of(Capability.of(ImapConstants.COMPRESS_COMMAND.getName() + "=" + ALGO));
    private final StatusResponseFactory factory;
    private static final String COMPRESSED = "COMPRESSED";

    public CompressProcessor(ImapProcessor next, StatusResponseFactory factory) {
        super(CompressRequest.class, next);
        this.factory = factory;
    }

    @Override
    protected void doProcess(CompressRequest request, Responder responder, ImapSession session) {
        if (session.isCompressionSupported()) {
            Object obj = session.getAttribute(COMPRESSED);
            if (obj != null) {
                responder.respond(factory.taggedNo(request.getTag(), request.getCommand(), HumanReadableText.COMPRESS_ALREADY_ACTIVE));
            } else {
                if (request.getAlgorithm().equalsIgnoreCase(ALGO) == false) {
                    responder.respond(factory.taggedBad(request.getTag(), request.getCommand(), HumanReadableText.ILLEGAL_ARGUMENTS));
                } else {
                    responder.respond(factory.taggedOk(request.getTag(), request.getCommand(), HumanReadableText.DEFLATE_ACTIVE));

                    if (session.startCompression()) {
                        session.setAttribute(COMPRESSED, true);
                    }
                }
            }
        } else {
            responder.respond(factory.taggedBad(request.getTag(), request.getCommand(), HumanReadableText.UNKNOWN_COMMAND));
        }
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        if (session.isCompressionSupported()) {
            return CAPA;
        }
        return Collections.emptyList();
    }

    @Override
    protected Closeable addContextToMDC(CompressRequest message) {
        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "COMPRESS")
            .addContext("algorithm", message.getAlgorithm())
            .build();
    }
}
