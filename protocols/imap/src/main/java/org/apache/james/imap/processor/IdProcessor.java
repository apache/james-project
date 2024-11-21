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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.apache.james.imap.api.ImapConfiguration;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.IDRequest;
import org.apache.james.imap.message.response.IdResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.MDCStructuredLogger;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

public class IdProcessor extends AbstractMailboxProcessor<IDRequest> implements CapabilityImplementingProcessor {
    public static final String MDC_KEY = "bound_MDC";
    private static final Logger LOGGER = LoggerFactory.getLogger(IdProcessor.class);
    private static final ImmutableList<Capability> CAPABILITIES = ImmutableList.of(Capability.of("ID"));

    private final Map<String, String> fields = new HashMap<>();

    @Inject
    public IdProcessor(MailboxManager mailboxManager, StatusResponseFactory factory, MetricFactory metricFactory) {
        super(IDRequest.class, mailboxManager, factory, metricFactory);
    }

    @Override
    public void configure(ImapConfiguration imapConfiguration) {
        super.configure(imapConfiguration);
        fields.putAll(imapConfiguration.getIdFieldsResponse());
    }

    @Override
    protected Mono<Void> processRequestReactive(IDRequest request, ImapSession session, Responder responder) {
        responder.respond(new IdResponse(fields));

        String mailUserAgent = request.getParameters().map(Object::toString).orElse("NIL");
        addMailUserAgentToMDC(session, mailUserAgent);
        session.setAttribute("userAgent", mailUserAgent);

        return logMailUserAgent(mailUserAgent)
            .then(unsolicitedResponses(session, responder, false))
            .then(Mono.fromRunnable(() -> okComplete(request, responder)));
    }

    private Mono<Void> logMailUserAgent(String mailUserAgent) {
        return ReactorUtils.logAsMono(() -> MDCStructuredLogger.forLogger(LOGGER)
            .field("parameters", mailUserAgent)
            .log(logger -> logger.info("Received id information")));
    }

    private void addMailUserAgentToMDC(ImapSession session, String mailUserAgent) {
        Object maybeMDC = session.getAttribute(MDC_KEY);
        if (maybeMDC instanceof MDCBuilder boundMDC) {
            MDCBuilder newMDC = boundMDC.addToContext("mailUserAgent", mailUserAgent);
            session.setAttribute(MDC_KEY, newMDC);
        }
    }

    @Override
    protected MDCBuilder mdc(IDRequest message) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "ID");
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        return CAPABILITIES;
    }
}
