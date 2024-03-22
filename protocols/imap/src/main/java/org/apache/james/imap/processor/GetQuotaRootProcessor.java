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

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.GetQuotaRootRequest;
import org.apache.james.imap.message.response.QuotaResponse;
import org.apache.james.imap.message.response.QuotaRootResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

/**
 * GETQUOTAROOT Processor
 */
public class GetQuotaRootProcessor extends AbstractMailboxProcessor<GetQuotaRootRequest> implements CapabilityImplementingProcessor {
    private static final List<Capability> CAPABILITIES = ImmutableList.of(ImapConstants.SUPPORTS_QUOTA, ImapConstants.SUPPORTS_QUOTA_RES_MESSAGE, ImapConstants.SUPPORTS_QUOTA_RES_STORAGE);

    private final QuotaRootResolver quotaRootResolver;
    private final QuotaManager quotaManager;

    @Inject
    public GetQuotaRootProcessor(MailboxManager mailboxManager, StatusResponseFactory factory, QuotaRootResolver quotaRootResolver, QuotaManager quotaManager,
                                 MetricFactory metricFactory) {
        super(GetQuotaRootRequest.class, mailboxManager, factory, metricFactory);
        this.quotaRootResolver = quotaRootResolver;
        this.quotaManager = quotaManager;
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        return CAPABILITIES;
    }

    @Override
    protected Mono<Void> processRequestReactive(GetQuotaRootRequest request, ImapSession session, Responder responder) {
        final MailboxSession mailboxSession = session.getMailboxSession();
        final MailboxManager mailboxManager = getMailboxManager();

        final MailboxPath mailboxPath = PathConverter.forSession(session).buildFullPath(request.getMailboxName());

        // First check mailbox exists
        return Mono.from(mailboxManager.getMailboxReactive(mailboxPath, mailboxSession))
            .flatMap(Throwing.function(messageManager -> {
                if (mailboxManager.hasRight(messageManager.getMailboxEntity(), MailboxACL.Right.Read, mailboxSession)) {
                    return respondQuotas(request, responder, mailboxPath);
                } else {
                    Object[] params = new Object[]{
                        MailboxACL.Right.Read.toString(),
                        request.getCommand().getName(),
                        request.getMailboxName()
                    };
                    HumanReadableText humanReadableText = new HumanReadableText(HumanReadableText.UNSUFFICIENT_RIGHTS_KEY, HumanReadableText.UNSUFFICIENT_RIGHTS_DEFAULT_VALUE, params);
                    no(request, responder, humanReadableText);
                    return Mono.empty();
                }
            }));
    }

    private Mono<Void> respondQuotas(GetQuotaRootRequest request, Responder responder, MailboxPath mailboxPath) {
        return Mono.from(quotaRootResolver.getQuotaRootReactive(mailboxPath))
            .flatMap(quotaRoot -> Mono.from(quotaManager.getQuotasReactive(quotaRoot))
                .doOnNext(quotas -> {
                    Quota<QuotaCountLimit, QuotaCountUsage> messageQuota = quotas.getMessageQuota();
                    Quota<QuotaSizeLimit, QuotaSizeUsage> storageQuota = quotas.getStorageQuota();
                    responder.respond(new QuotaRootResponse(request.getMailboxName(), quotaRoot.getValue()));
                    if (messageQuota.getLimit().isLimited()) {
                        responder.respond(new QuotaResponse(ImapConstants.MESSAGE_QUOTA_RESOURCE, quotaRoot.getValue(), messageQuota));
                    }
                    if (storageQuota.getLimit().isLimited()) {
                        responder.respond(new QuotaResponse(ImapConstants.STORAGE_QUOTA_RESOURCE, quotaRoot.getValue(), storageQuota));
                    }
                    okComplete(request, responder);
                }))
            .onErrorResume(MailboxException.class, e -> {
                taggedBad(request, responder, HumanReadableText.FAILURE_NO_SUCH_MAILBOX);
                return Mono.empty();
            })
            .then();
    }

    @Override
    protected MDCBuilder mdc(GetQuotaRootRequest request) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "GET_QUOTA_ROOT")
            .addToContext("mailbox", request.getMailboxName());
    }
}
