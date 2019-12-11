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

import org.apache.james.imap.api.message.request.ImapRequest;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.metrics.api.MetricFactory;

/**
 * Abstract base class which should be used by implementations which need to
 * access the {@link SubscriptionManager}
 */
public abstract class AbstractSubscriptionProcessor<R extends ImapRequest> extends AbstractMailboxProcessor<R> {

    private final SubscriptionManager subscriptionManager;

    public AbstractSubscriptionProcessor(Class<R> acceptableClass, ImapProcessor next, MailboxManager mailboxManager, SubscriptionManager subscriptionManager, StatusResponseFactory factory,
                                         MetricFactory metricFactory) {
        super(acceptableClass, next, mailboxManager, factory, metricFactory);
        this.subscriptionManager = subscriptionManager;
    }

    /**
     * Return the {@link SubscriptionManager}
     * 
     * @return subscriptionManager
     */
    protected SubscriptionManager getSubscriptionManager() {
        return subscriptionManager;
    }

    @Override
    protected final void processRequest(R request, ImapSession session, Responder responder) {

        // take care of calling the start/end processing
        MailboxSession mSession = session.getMailboxSession();
        getSubscriptionManager().startProcessingRequest(mSession);
        doProcessRequest(request, session, responder);
        getSubscriptionManager().endProcessingRequest(mSession);
    }

    /**
     * Process the request
     * 
     * @param request
     * @param session
     * @param responder
     */
    protected abstract void doProcessRequest(R request, ImapSession session, Responder responder);

}
