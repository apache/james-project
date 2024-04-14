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

import static org.apache.james.imap.api.ImapConstants.SUPPORTS_XLIST;

import java.util.Collections;
import java.util.List;

import jakarta.inject.Inject;

import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.MailboxType;
import org.apache.james.imap.api.process.MailboxTyper;
import org.apache.james.imap.message.request.ListRequest;
import org.apache.james.imap.message.request.XListRequest;
import org.apache.james.imap.message.response.XListResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.metrics.api.MetricFactory;

import com.google.common.collect.ImmutableList;

/**
 * Processes XLIST command
 */
public class XListProcessor extends ListProcessor<XListRequest> implements CapabilityImplementingProcessor {

    private static final List<Capability> XLIST_CAPS = ImmutableList.of(SUPPORTS_XLIST);

    @Inject
    public XListProcessor(MailboxManager mailboxManager, StatusResponseFactory factory, MetricFactory metricFactory,
                          SubscriptionManager subscriptionManager) {
        this(mailboxManager, factory, null, metricFactory, subscriptionManager);
    }

    public XListProcessor(MailboxManager mailboxManager, StatusResponseFactory factory, MailboxTyper mailboxTyper,
                          MetricFactory metricFactory, SubscriptionManager subscriptionManager) {
        super(XListRequest.class, mailboxManager, factory, metricFactory, subscriptionManager, null, mailboxTyper);
    }

    @Override
    public List<Capability> getImplementedCapabilities(ImapSession session) {
        // if there's no mailboxTyper, do not annnoyce XLIST capability
        if (mailboxTyper == null) {
            return Collections.emptyList();
        }

        return XLIST_CAPS;
    }

    @Override
    protected ImapResponseMessage createResponse(MailboxMetaData.Children children, MailboxMetaData.Selectability selectability,
                                                 String name, char hierarchyDelimiter, MailboxType type, boolean isSubscribed) {
        return new XListResponse(children, selectability, name, hierarchyDelimiter, type);
    }

    @Override
    protected MailboxType getMailboxType(ListRequest request, ImapSession session, MailboxPath path) {
        return mailboxTyper.getMailboxType(session, path);
    }
}
