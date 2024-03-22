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

import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.SetQuotaRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

/**
 * SETQUOTA processor
 */
public class SetQuotaProcessor extends AbstractMailboxProcessor<SetQuotaRequest> implements CapabilityImplementingProcessor {
    private static final List<Capability> CAPABILITIES = ImmutableList.of(ImapConstants.SUPPORTS_QUOTA);

    @Inject
    public SetQuotaProcessor(MailboxManager mailboxManager, StatusResponseFactory factory,
                             MetricFactory metricFactory) {
        super(SetQuotaRequest.class, mailboxManager, factory, metricFactory);
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        return CAPABILITIES;
    }

    @Override
    protected Mono<Void> processRequestReactive(SetQuotaRequest request, ImapSession session, Responder responder) {
        return Mono.fromRunnable(() -> {
            Object[] params = new Object[]{
                "Full admin rights",
                request.getCommand().getName(),
                "Can not perform SETQUOTA commands"
            };
            HumanReadableText humanReadableText = new HumanReadableText(HumanReadableText.UNSUFFICIENT_RIGHTS_KEY, HumanReadableText.UNSUFFICIENT_RIGHTS_DEFAULT_VALUE, params);
            no(request, responder, humanReadableText);
        });
    }

    @Override
    protected MDCBuilder mdc(SetQuotaRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "SET_QUOTA")
            .addToContext("quotaRoot", request.getQuotaRoot())
            .addToContext("limits", request.getResourceLimits().toString());
    }
}
