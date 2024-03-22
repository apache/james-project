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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.request.ImapRequest;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.EnableRequest;
import org.apache.james.imap.message.response.EnableResponse;
import org.apache.james.imap.processor.PermitEnableCapabilityProcessor.EnableException;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class EnableProcessor extends AbstractMailboxProcessor<EnableRequest> implements CapabilityImplementingProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnableProcessor.class);

    private static final List<PermitEnableCapabilityProcessor> capabilities = new ArrayList<>();
    public static final String ENABLED_CAPABILITIES = "ENABLED_CAPABILITIES";
    private static final List<Capability> CAPS = ImmutableList.of(SUPPORTS_ENABLE);
    private final CapabilityProcessor capabilityProcessor;

    public EnableProcessor(MailboxManager mailboxManager, StatusResponseFactory factory, List<PermitEnableCapabilityProcessor> capabilities,
            MetricFactory metricFactory, CapabilityProcessor capabilityProcessor) {
        this(mailboxManager, factory, metricFactory, capabilityProcessor);
        EnableProcessor.capabilities.addAll(capabilities);
    }

    @Inject
    public EnableProcessor(MailboxManager mailboxManager, StatusResponseFactory factory,
                           MetricFactory metricFactory, CapabilityProcessor capabilityProcessor) {
        super(EnableRequest.class, mailboxManager, factory, metricFactory);
        this.capabilityProcessor = capabilityProcessor;
    }


    @Override
    protected Mono<Void> processRequestReactive(EnableRequest request, ImapSession session, Responder responder) {
        List<Capability> caps = request.getCapabilities();
        return enable(request, responder, session, caps)
            .doOnNext(enabledCaps -> responder.respond(new EnableResponse(enabledCaps)))
            .then(unsolicitedResponses(session, responder, false))
            .then(Mono.fromRunnable(() -> okComplete(request, responder)))
            .onErrorResume(EnableException.class, e -> {
                taggedBad(request, responder, HumanReadableText.FAILED);
                return ReactorUtils.logAsMono(() -> LOGGER.info("Unable to enable extension", e));
            }).then();
    }
   
    public Mono<Set<Capability>> enable(ImapRequest request, Responder responder, ImapSession session, List<Capability> caps) {
        return Flux.fromIterable(caps)
            .flatMap(cap -> {
                // Check if the CAPABILITY is supported at all
                if (capabilityProcessor.getSupportedCapabilities(session).contains(cap)) {
                    return Flux.fromIterable(capabilities)
                        .flatMap(enableProcessor -> {
                            if (enableProcessor.getPermitEnableCapabilities(session).contains(cap)) {
                                return enableProcessor.enable(request, responder, session, cap)
                                    .then(Mono.just(cap));
                            }
                            return Mono.empty();
                        })
                        .distinct()
                        .next();
                }
                return Mono.empty();
            }).collect(ImmutableSet.toImmutableSet())
            .map(enabledCaps -> {
                getEnabledCapabilities(session).addAll(enabledCaps);
                return enabledCaps;
            });
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
    protected MDCBuilder mdc(EnableRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "ENABLE")
            .addToContext("capabilities", ImmutableList.copyOf(request.getCapabilities()).toString());
    }
}
