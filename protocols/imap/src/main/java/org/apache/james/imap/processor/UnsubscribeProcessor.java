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

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.UnsubscribeRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnsubscribeProcessor extends AbstractSubscriptionProcessor<UnsubscribeRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(UnsubscribeProcessor.class);

    public UnsubscribeProcessor(ImapProcessor next, MailboxManager mailboxManager, SubscriptionManager subscriptionManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(UnsubscribeRequest.class, next, mailboxManager, subscriptionManager, factory, metricFactory);
    }

    @Override
    protected void doProcessRequest(UnsubscribeRequest request, ImapSession session, String tag, ImapCommand command, Responder responder) {
        final String mailboxName = request.getMailboxName();
        final MailboxSession mailboxSession = ImapSessionUtils.getMailboxSession(session);
        try {
            getSubscriptionManager().unsubscribe(mailboxSession, mailboxName);

            unsolicitedResponses(session, responder, false);
            okComplete(command, tag, responder);

        } catch (SubscriptionException e) {
            LOGGER.info("Unsubscribe failed for mailbox {}", mailboxName, e);
            unsolicitedResponses(session, responder, false);

            no(command, tag, responder, HumanReadableText.GENERIC_SUBSCRIPTION_FAILURE);
        }
    }

    @Override
    protected Closeable addContextToMDC(UnsubscribeRequest message) {
        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "UNSUBSCRIBE")
            .addContext("mailbox", message.getMailboxName())
            .build();
    }
}
