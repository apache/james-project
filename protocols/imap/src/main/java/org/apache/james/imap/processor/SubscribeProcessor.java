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

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.SubscribeRequest;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.exception.SubscriptionException;

public class SubscribeProcessor extends AbstractSubscriptionProcessor<SubscribeRequest> {

    public SubscribeProcessor(ImapProcessor next, MailboxManager mailboxManager, SubscriptionManager subscriptionManager, StatusResponseFactory factory) {
        super(SubscribeRequest.class, next, mailboxManager, subscriptionManager, factory);
    }

    /**
     * @see org.apache.james.imap.processor.AbstractSubscriptionProcessor
     * #doProcessRequest(org.apache.james.imap.api.message.request.ImapRequest,
     * org.apache.james.imap.api.process.ImapSession, java.lang.String,
     * org.apache.james.imap.api.ImapCommand,
     * org.apache.james.imap.api.process.ImapProcessor.Responder)
     */
    protected void doProcessRequest(SubscribeRequest request, ImapSession session, String tag, ImapCommand command, Responder responder) {
        final String mailboxName = request.getMailboxName();
        final MailboxSession mailboxSession = ImapSessionUtils.getMailboxSession(session);
        try {
            getSubscriptionManager().subscribe(mailboxSession, mailboxName);

            unsolicitedResponses(session, responder, false);
            okComplete(command, tag, responder);

        } catch (SubscriptionException e) {
            if (session.getLog().isInfoEnabled()) {
                session.getLog().info("Subscribe failed for mailbox " + mailboxName, e);
            }
            unsolicitedResponses(session, responder, false);
            no(command, tag, responder, HumanReadableText.GENERIC_SUBSCRIPTION_FAILURE);
        }
    }

}
