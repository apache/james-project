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

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.MailboxType;
import org.apache.james.imap.api.process.MailboxTyper;
import org.apache.james.imap.message.request.ListRequest;
import org.apache.james.imap.message.request.XListRequest;
import org.apache.james.imap.message.response.XListResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.MetricFactory;

import com.google.common.collect.ImmutableList;

/**
 * Processes XLIST command
 */
public class XListProcessor extends ListProcessor implements CapabilityImplementingProcessor {

    private final static List<String> XLIST_CAPS = ImmutableList.of(SUPPORTS_XLIST);
    private final MailboxTyper mailboxTyper;

    // some interface
    public XListProcessor(ImapProcessor next, MailboxManager mailboxManager, StatusResponseFactory factory, MailboxTyper mailboxTyper,
            MetricFactory metricFactory) {
        super(next, mailboxManager, factory, metricFactory);
        this.mailboxTyper = mailboxTyper;
    }

    /**
     * @see org.apache.james.imap.processor.CapabilityImplementingProcessor
     * #getImplementedCapabilities(org.apache.james.imap.api.process.ImapSession)
     */
    public List<String> getImplementedCapabilities(ImapSession session) {
        // if there's no mailboxTyper, do not annnoyce XLIST capability
        if (mailboxTyper == null) {
            return Collections.emptyList();
        }

        return XLIST_CAPS;
    }

    @Override
    protected boolean isAcceptable(ImapMessage message) {
        return (message instanceof XListRequest);
    }

    @Override
    protected void doProcess(ListRequest message, ImapSession session, String tag, ImapCommand command, Responder responder) {
        final XListRequest request = (XListRequest) message;
        final String baseReferenceName = request.getBaseReferenceName();
        final String mailboxPatternString = request.getMailboxPattern();
        doProcess(baseReferenceName, mailboxPatternString, session, tag, command, responder, mailboxTyper);
    }

    @Override
    protected ImapResponseMessage createResponse(boolean noInferior, boolean noSelect, boolean marked, boolean unmarked, boolean hasChildren, boolean hasNoChildren, String mailboxName, char delimiter, MailboxType type) {
        return new XListResponse(noInferior, noSelect, marked, unmarked, hasChildren, hasNoChildren, mailboxName, delimiter, type);
    }
}
