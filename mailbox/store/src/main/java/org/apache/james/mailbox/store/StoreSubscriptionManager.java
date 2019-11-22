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
package org.apache.james.mailbox.store;

import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.RequestAware;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.store.transaction.Mapper;
import org.apache.james.mailbox.store.user.SubscriptionMapper;
import org.apache.james.mailbox.store.user.SubscriptionMapperFactory;
import org.apache.james.mailbox.store.user.model.Subscription;

/**
 * Manages subscriptions for Users and Mailboxes.
 */
public class StoreSubscriptionManager implements SubscriptionManager {
    private static final int INITIAL_SIZE = 32;
    
    protected SubscriptionMapperFactory mapperFactory;

    @Inject
    public StoreSubscriptionManager(SubscriptionMapperFactory mapperFactory) {
        this.mapperFactory = mapperFactory;
    }

    @Override
    public void subscribe(MailboxSession session, String mailbox) throws SubscriptionException {
        SubscriptionMapper mapper = mapperFactory.getSubscriptionMapper(session);
        try {
            mapper.execute(Mapper.toTransaction(() -> {
                Subscription newSubscription = new Subscription(session.getUser(), mailbox);
                mapper.save(newSubscription);
            }));
        } catch (MailboxException e) {
            throw new SubscriptionException(e);
        }
    }

    @Override
    public Collection<String> subscriptions(MailboxSession session) throws SubscriptionException {
        return mapperFactory.getSubscriptionMapper(session)
            .findSubscriptionsForUser(session.getUser())
            .stream()
            .map(Subscription::getMailbox)
            .collect(Collectors.toCollection(() -> new HashSet<>(INITIAL_SIZE)));
    }

    @Override
    public void unsubscribe(MailboxSession session, String mailbox) throws SubscriptionException {
        SubscriptionMapper mapper = mapperFactory.getSubscriptionMapper(session);
        try {
            mapper.execute(Mapper.toTransaction(() -> mapper.delete(new Subscription(session.getUser(), mailbox))));
        } catch (MailboxException e) {
            throw new SubscriptionException(e);
        }
    }

    @Override
    public void endProcessingRequest(MailboxSession session) {
        if (mapperFactory instanceof RequestAware) {
            ((RequestAware)mapperFactory).endProcessingRequest(session);
        }
    }

    @Override
    public void startProcessingRequest(MailboxSession session) {
        // Do nothing        
    }
}
