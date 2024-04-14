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
import org.apache.james.imap.message.request.GetQuotaRequest;
import org.apache.james.imap.message.response.QuotaResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * GETQUOTA processor
 */
public class GetQuotaProcessor extends AbstractMailboxProcessor<GetQuotaRequest> implements CapabilityImplementingProcessor {

    private static final List<Capability> CAPABILITIES = ImmutableList.of(ImapConstants.SUPPORTS_QUOTA);

    private final QuotaManager quotaManager;
    private final QuotaRootResolver quotaRootResolver;

    @Inject
    public GetQuotaProcessor(MailboxManager mailboxManager, StatusResponseFactory factory, QuotaManager quotaManager, QuotaRootResolver quotaRootResolver,
                             MetricFactory metricFactory) {
        super(GetQuotaRequest.class, mailboxManager, factory, metricFactory);
        this.quotaManager = quotaManager;
        this.quotaRootResolver = quotaRootResolver;
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        return CAPABILITIES;
    }

    @Override
    protected Mono<Void> processRequestReactive(GetQuotaRequest request, ImapSession session, Responder responder) {
        try {
            QuotaRoot quotaRoot = quotaRootResolver.fromString(request.getQuotaRoot());
            return hasRight(quotaRoot, session)
                .flatMap(hasRight -> {
                    if (hasRight) {
                        return Mono.from(quotaManager.getQuotasReactive(quotaRoot))
                            .doOnNext(quotas -> respond(responder, request, quotaRoot, quotas));
                    }
                    return Mono.fromRunnable(() -> respondNo(request, responder));
                }).then()
                .onErrorResume(MailboxException.class, e -> {
                    taggedBad(request, responder, HumanReadableText.FAILURE_NO_SUCH_MAILBOX);
                    return Mono.empty();
                });
        } catch (MailboxException me) {
            taggedBad(request, responder, HumanReadableText.FAILURE_NO_SUCH_MAILBOX);
            return Mono.empty();
        }
    }

    private void respondNo(GetQuotaRequest request, Responder responder) {
        Object[] params = new Object[]{
                MailboxACL.Right.Read.toString(),
                request.getCommand().getName(),
                "Any mailbox of this user USER"
        };
        HumanReadableText humanReadableText = new HumanReadableText(HumanReadableText.UNSUFFICIENT_RIGHTS_KEY, HumanReadableText.UNSUFFICIENT_RIGHTS_DEFAULT_VALUE, params);
        no(request, responder, humanReadableText);
    }

    private void respond(Responder responder, GetQuotaRequest request, QuotaRoot quotaRoot, QuotaManager.Quotas quotas) {
        if (quotas.getMessageQuota().getLimit().isLimited()) {
            responder.respond(new QuotaResponse(ImapConstants.MESSAGE_QUOTA_RESOURCE, quotaRoot.getValue(), quotas.getMessageQuota()));
        }
        if (quotas.getStorageQuota().getLimit().isLimited()) {
            responder.respond(new QuotaResponse(ImapConstants.STORAGE_QUOTA_RESOURCE, quotaRoot.getValue(), quotas.getStorageQuota()));
        }
        okComplete(request, responder);
    }

    private Mono<Boolean> hasRight(QuotaRoot quotaRoot, ImapSession session) {
        // If any of the mailboxes owned by quotaRoot user can be read by the current user, then we should respond to him.
        final MailboxSession mailboxSession = session.getMailboxSession();
        return Flux.from(quotaRootResolver.retrieveAssociatedMailboxes(quotaRoot, mailboxSession))
            .filter(Throwing.<Mailbox>predicate(mailbox -> getMailboxManager().hasRight(mailbox, MailboxACL.Right.Read, mailboxSession)).sneakyThrow())
            .hasElements();
    }

    @Override
    protected MDCBuilder mdc(GetQuotaRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "GET_QUOTA")
            .addToContext("quotaRoot", request.getQuotaRoot());
    }
}
