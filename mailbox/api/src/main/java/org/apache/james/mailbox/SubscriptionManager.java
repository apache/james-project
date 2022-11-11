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

package org.apache.james.mailbox;

import java.util.Collection;

import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.model.MailboxPath;
import org.reactivestreams.Publisher;

/**
 * Subscribes mailboxes to users. This is only needed to implement if the Mailbox should be usable via
 * IMAP. For POP3 only you don't need this at all.
 * 
 */
public interface SubscriptionManager extends RequestAware {
    /**
     * Subscribes the user in the session to the given mailbox.
     *
     * This mailbox may be shared or belong to another user.
     *
     * The corresponding path do NOT need to exist.
     *
     * @param session
     *            not null
     * @param mailbox
     *            not null
     * @throws SubscriptionException
     *             when subscription fails
     */
    void subscribe(MailboxSession session, MailboxPath mailbox) throws SubscriptionException;

    Publisher<Void> subscribeReactive(MailboxPath mailbox, MailboxSession session);

    /**
     * Finds all subscriptions for the user in the session.
     * 
     * @param session
     *            not null
     * @return not null
     * @throws SubscriptionException
     *             when subscriptions cannot be read
     */
    Collection<MailboxPath> subscriptions(MailboxSession session) throws SubscriptionException;

    Publisher<MailboxPath> subscriptionsReactive(MailboxSession session) throws SubscriptionException;

    void unsubscribe(MailboxSession session, MailboxPath mailbox) throws SubscriptionException;

    Publisher<Void> unsubscribeReactive(MailboxPath mailbox, MailboxSession session);
}
