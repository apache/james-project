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

package org.apache.james.examples.imap;

import java.util.List;
import java.util.Properties;

import jakarta.inject.Inject;

import org.apache.james.imap.api.ImapConfiguration;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.processor.CapabilityImplementingProcessor;
import org.apache.james.imap.processor.base.AbstractProcessor;
import org.apache.james.util.MDCBuilder;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

public class PingProcessor extends AbstractProcessor<PingImapPackages.PingRequest> implements CapabilityImplementingProcessor {
    private final StatusResponseFactory factory;
    private String pongResponse;

    @Inject
    public PingProcessor(StatusResponseFactory factory) {
        super(PingImapPackages.PingRequest.class);
        this.factory = factory;
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        return ImmutableList.of(Capability.of("PING"));
    }

    @Override
    public void configure(ImapConfiguration imapConfiguration) {
        Properties customProperties = imapConfiguration.getCustomProperties();

        pongResponse = (String) customProperties
            .getOrDefault("pong.response", "completed.");
    }

    @Override
    protected Mono<Void> doProcess(PingImapPackages.PingRequest request, Responder responder, ImapSession session) {
        return Mono.fromRunnable(() -> responder.respond(new PingImapPackages.PingResponse()))
            .then(Mono.fromRunnable(() -> responder.respond(
                factory.taggedOk(request.getTag(), request.getCommand(), new HumanReadableText("org.apache.james.imap.COMPLETED", pongResponse)))));
    }

    @Override
    protected MDCBuilder mdc(PingImapPackages.PingRequest message) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "PING");
    }
}