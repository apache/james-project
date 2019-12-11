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
import java.util.List;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.GetQuotaRequest;
import org.apache.james.imap.message.response.QuotaResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;

import com.google.common.collect.ImmutableList;

/**
 * GETQUOTA processor
 */
public class GetQuotaProcessor extends AbstractMailboxProcessor<GetQuotaRequest> implements CapabilityImplementingProcessor {

    private static final List<String> CAPABILITIES = ImmutableList.of(ImapConstants.SUPPORTS_QUOTA);

    private final QuotaManager quotaManager;
    private final QuotaRootResolver quotaRootResolver;

    public GetQuotaProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory, QuotaManager quotaManager, QuotaRootResolver quotaRootResolver,
            MetricFactory metricFactory) {
        super(GetQuotaRequest.class, next, mailboxManager, factory, metricFactory);
        this.quotaManager = quotaManager;
        this.quotaRootResolver = quotaRootResolver;
    }

    @Override
    public List<String> getImplementedCapabilities(ImapSession session) {
        return CAPABILITIES;
    }

    @Override
    protected void processRequest(GetQuotaRequest request, ImapSession session, Responder responder) {
        try {
            QuotaRoot quotaRoot = quotaRootResolver.fromString(request.getQuotaRoot());
            if (hasRight(quotaRoot, session)) {
                Quota<QuotaCountLimit, QuotaCountUsage> messageQuota = quotaManager.getMessageQuota(quotaRoot);
                Quota<QuotaSizeLimit, QuotaSizeUsage> storageQuota = quotaManager.getStorageQuota(quotaRoot);
                if (messageQuota.getLimit().isLimited()) {
                    responder.respond(new QuotaResponse(ImapConstants.MESSAGE_QUOTA_RESOURCE, quotaRoot.getValue(), messageQuota));
                }
                if (storageQuota.getLimit().isLimited()) {
                    responder.respond(new QuotaResponse(ImapConstants.STORAGE_QUOTA_RESOURCE, quotaRoot.getValue(), storageQuota));
                }
                okComplete(request, responder);
            } else {
                Object[] params = new Object[]{
                        MailboxACL.Right.Read.toString(),
                        request.getCommand().getName(),
                        "Any mailbox of this user USER"
                };
                HumanReadableText humanReadableText = new HumanReadableText(HumanReadableText.UNSUFFICIENT_RIGHTS_KEY, HumanReadableText.UNSUFFICIENT_RIGHTS_DEFAULT_VALUE, params);
                no(request, responder, humanReadableText);
            }
        } catch (MailboxException me) {
            taggedBad(request, responder, HumanReadableText.FAILURE_NO_SUCH_MAILBOX);
        }
    }

    private boolean hasRight(QuotaRoot quotaRoot, ImapSession session) throws MailboxException {
        // If any of the mailboxes owned by quotaRoot user can be read by the current user, then we should respond to him.
        final MailboxSession mailboxSession = session.getMailboxSession();
        List<Mailbox> mailboxList = quotaRootResolver.retrieveAssociatedMailboxes(quotaRoot, mailboxSession);
        for (Mailbox mailbox : mailboxList) {
            if (getMailboxManager().hasRight(mailbox.generateAssociatedPath(), MailboxACL.Right.Read, mailboxSession)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected Closeable addContextToMDC(GetQuotaRequest request) {
        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "GET_QUOTA")
            .addContext("quotaRoot", request.getQuotaRoot())
            .build();
    }
}
