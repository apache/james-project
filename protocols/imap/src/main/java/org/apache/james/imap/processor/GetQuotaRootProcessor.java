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

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
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
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.quota.QuotaSize;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;

import com.google.common.collect.ImmutableList;

/**
 * GETQUOTAROOT Processor
 */
public class GetQuotaRootProcessor extends AbstractMailboxProcessor<GetQuotaRootRequest> implements CapabilityImplementingProcessor {

    private static final List<String> CAPABILITIES = ImmutableList.of(ImapConstants.SUPPORTS_QUOTA);
    private final QuotaRootResolver quotaRootResolver;
    private final QuotaManager quotaManager;

    public GetQuotaRootProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory, QuotaRootResolver quotaRootResolver, QuotaManager quotaManager,
            MetricFactory metricFactory) {
        super(GetQuotaRootRequest.class, next, mailboxManager, factory, metricFactory);
        this.quotaRootResolver = quotaRootResolver;
        this.quotaManager = quotaManager;
    }

    public List<String> getImplementedCapabilities(ImapSession session) {
        return CAPABILITIES;
    }

    @Override
    protected void doProcess(GetQuotaRootRequest message, ImapSession session, String tag, ImapCommand command, Responder responder) {
        final MailboxSession mailboxSession = ImapSessionUtils.getMailboxSession(session);
        final MailboxManager mailboxManager = getMailboxManager();

        final MailboxPath mailboxPath = PathConverter.forSession(session).buildFullPath(message.getMailboxName());

        // First check mailbox exists
        try {
            if (mailboxManager.hasRight(mailboxPath, MailboxACL.Right.Read, mailboxSession)) {
                QuotaRoot quotaRoot = quotaRootResolver.getQuotaRoot(mailboxPath);
                Quota<QuotaCount> messageQuota = quotaManager.getMessageQuota(quotaRoot);
                Quota<QuotaSize> storageQuota = quotaManager.getStorageQuota(quotaRoot);
                responder.respond(new QuotaRootResponse(message.getMailboxName(), quotaRoot.getValue()));
                if (messageQuota.getMax().isLimited()) {
                    responder.respond(new QuotaResponse(ImapConstants.MESSAGE_QUOTA_RESOURCE, quotaRoot.getValue(), messageQuota));
                }
                if (storageQuota.getMax().isLimited()) {
                    responder.respond(new QuotaResponse(ImapConstants.STORAGE_QUOTA_RESOURCE, quotaRoot.getValue(), storageQuota));
                }
                okComplete(command, tag, responder);
            } else {
                Object[] params = new Object[]{
                        MailboxACL.Right.Read.toString(),
                        command.getName(),
                        message.getMailboxName()
                };
                HumanReadableText humanReadableText = new HumanReadableText(HumanReadableText.UNSUFFICIENT_RIGHTS_KEY, HumanReadableText.UNSUFFICIENT_RIGHTS_DEFAULT_VALUE, params);
                no(command, tag, responder, humanReadableText);
            }
        } catch (MailboxException me) {
            taggedBad(command, tag, responder, HumanReadableText.FAILURE_NO_SUCH_MAILBOX);
        }

    }

    @Override
    protected Closeable addContextToMDC(GetQuotaRootRequest message) {
        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "GET_QUOTA_ROOT")
            .addContext("mailbox", message.getMailboxName())
            .build();
    }
}
