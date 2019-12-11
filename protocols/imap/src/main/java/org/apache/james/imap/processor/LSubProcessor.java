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
import java.util.ArrayList;
import java.util.Collection;

import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.display.ModifiedUtf7;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.LsubRequest;
import org.apache.james.imap.message.response.LSubResponse;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.model.search.MailboxNameExpression;
import org.apache.james.mailbox.model.search.PrefixedRegex;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LSubProcessor extends AbstractSubscriptionProcessor<LsubRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LSubProcessor.class);

    public LSubProcessor(ImapProcessor next, MailboxManager mailboxManager, SubscriptionManager subscriptionManager, StatusResponseFactory factory,
            MetricFactory metricFactory) {
        super(LsubRequest.class, next, mailboxManager, subscriptionManager, factory, metricFactory);
    }

    @Override
    protected void doProcessRequest(LsubRequest request, ImapSession session, Responder responder) {
        String referenceName = request.getBaseReferenceName();
        String mailboxPattern = request.getMailboxPattern();

        try {
            listSubscriptions(session, responder, referenceName, mailboxPattern);

            okComplete(request, responder);
        } catch (MailboxException e) {
            LOGGER.error("LSub failed for reference {} and pattern {}", referenceName, mailboxPattern, e);
            no(request, responder, HumanReadableText.GENERIC_LSUB_FAILURE);
        }
    }

    private void listSubscriptions(ImapSession session, Responder responder, String referenceName, String mailboxName) throws SubscriptionException, MailboxException {
        MailboxSession mailboxSession = session.getMailboxSession();
        Collection<String> mailboxes = getSubscriptionManager().subscriptions(mailboxSession);

        String decodedMailName = ModifiedUtf7.decodeModifiedUTF7(referenceName);

        MailboxNameExpression expression = new PrefixedRegex(
            decodedMailName,
            ModifiedUtf7.decodeModifiedUTF7(mailboxName),
            mailboxSession.getPathDelimiter());
        Collection<String> mailboxResponses = new ArrayList<>();

        for (String mailbox : mailboxes) {
            respond(responder, expression, mailbox, true, mailboxes, mailboxResponses, mailboxSession.getPathDelimiter());
        }
    }

    private void respond(Responder responder, MailboxNameExpression expression, String mailboxName, boolean originalSubscription, Collection<String> mailboxes, Collection<String> mailboxResponses, char delimiter) {
        if (expression.isExpressionMatch(mailboxName)) {
            if (!mailboxResponses.contains(mailboxName)) {
                responder.respond(new LSubResponse(mailboxName, !originalSubscription, delimiter));
                mailboxResponses.add(mailboxName);
            }
        } else {
            int lastDelimiter = mailboxName.lastIndexOf(delimiter);
            if (lastDelimiter > 0) {
                String parentMailbox = mailboxName.substring(0, lastDelimiter);
                if (!mailboxes.contains(parentMailbox)) {
                    respond(responder, expression, parentMailbox, false, mailboxes, mailboxResponses, delimiter);
                }
            }
        }
    }

    @Override
    protected Closeable addContextToMDC(LsubRequest request) {
        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "LSUB")
            .addContext("base", request.getBaseReferenceName())
            .addContext("pattern", request.getMailboxPattern())
            .build();
    }
}
