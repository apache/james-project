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
import java.util.List;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.RequestAware;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.store.transaction.Mapper;
import org.apache.james.mailbox.store.user.SubscriptionMapper;
import org.apache.james.mailbox.store.user.SubscriptionMapperFactory;
import org.apache.james.mailbox.store.user.model.Subscription;
import org.apache.james.mailbox.store.user.model.impl.SimpleSubscription;

/**
 * Manages subscriptions for Users and Mailboxes.
 */
public class StoreSubscriptionManager implements SubscriptionManager {

    private static final int INITIAL_SIZE = 32;
    
    protected SubscriptionMapperFactory mapperFactory;
    
    public StoreSubscriptionManager(final SubscriptionMapperFactory mapperFactory) {
        this.mapperFactory = mapperFactory;
    }

    /**
     * @see org.apache.james.mailbox.SubscriptionManager#subscribe(org.apache.james.mailbox.MailboxSession, java.lang.String)
     */
    public void subscribe(final MailboxSession session, final String mailbox) throws SubscriptionException {
        final SubscriptionMapper mapper = mapperFactory.getSubscriptionMapper(session);
        try {
            mapper.execute(new Mapper.VoidTransaction() {

                public void runVoid() throws MailboxException {
                    final Subscription subscription = mapper.findMailboxSubscriptionForUser(session.getUser().getUserName(), mailbox);
                    if (subscription == null) {
                        final Subscription newSubscription = createSubscription(session, mailbox);
                        mapper.save(newSubscription);
                    }
                }
                
            });
        } catch (MailboxException e) {
            throw new SubscriptionException(e);
        }
    }

    /**
     * Create Subscription for the given user and mailbox. By default a {@link SimpleSubscription} will get returned.
     * 
     * If you need something more special just override this method
     * 
     * @param session
     * @param mailbox
     * @return subscription 
     */
    protected Subscription createSubscription(final MailboxSession session, final String mailbox) {
        return new SimpleSubscription(session.getUser().getUserName(), mailbox);
    }

    /**
     * @see org.apache.james.mailbox.SubscriptionManager#subscriptions(org.apache.james.mailbox.MailboxSession)
     */
    public Collection<String> subscriptions(final MailboxSession session) throws SubscriptionException {
        final SubscriptionMapper mapper = mapperFactory.getSubscriptionMapper(session);
        final List<Subscription> subscriptions = mapper.findSubscriptionsForUser(session.getUser().getUserName());
        final Collection<String> results = new HashSet<String>(INITIAL_SIZE);
        for (Subscription subscription:subscriptions) {
            results.add(subscription.getMailbox());
        }        
        return results;
    }

    /**
     * @see org.apache.james.mailbox.SubscriptionManager#unsubscribe(org.apache.james.mailbox.MailboxSession, java.lang.String)
     */
    public void unsubscribe(final MailboxSession session, final String mailbox) throws SubscriptionException {
        final SubscriptionMapper mapper = mapperFactory.getSubscriptionMapper(session);
        try {
            mapper.execute(new Mapper.VoidTransaction() {

                public void runVoid() throws MailboxException {
                    final Subscription subscription = mapper.findMailboxSubscriptionForUser(session.getUser().getUserName(), mailbox);
                    if (subscription != null) {
                        mapper.delete(subscription);
                    }
                }

            });
        } catch (MailboxException e) {
            throw new SubscriptionException(e);
        }
    }

    /**
     * @see org.apache.james.mailbox.SubscriptionManager#endProcessingRequest(org.apache.james.mailbox.MailboxSession)
     */
    public void endProcessingRequest(MailboxSession session) {
        if (mapperFactory instanceof RequestAware) {
            ((RequestAware)mapperFactory).endProcessingRequest(session);
        }
    }

    /**
     * Do nothing, Sub classes should override this if needed
     */
    public void startProcessingRequest(MailboxSession session) {
        // Do nothing        
    }
    
    
}
