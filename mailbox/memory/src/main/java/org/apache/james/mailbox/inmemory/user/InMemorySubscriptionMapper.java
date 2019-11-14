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
package org.apache.james.mailbox.inmemory.user;

import java.util.List;

import org.apache.james.core.Username;
import org.apache.james.mailbox.store.transaction.NonTransactionalMapper;
import org.apache.james.mailbox.store.user.SubscriptionMapper;
import org.apache.james.mailbox.store.user.model.Subscription;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;

public class InMemorySubscriptionMapper extends NonTransactionalMapper implements SubscriptionMapper {
    
    private final ListMultimap<Username, Subscription> subscriptionsByUser;
    
    public InMemorySubscriptionMapper() {
        subscriptionsByUser = Multimaps.synchronizedListMultimap(ArrayListMultimap.create());
    }

    @Override
    public synchronized void delete(Subscription subscription) {
        subscriptionsByUser.remove(subscription.getUser(), subscription);
    }

    @Override
    public Subscription findMailboxSubscriptionForUser(Username user, String mailbox) {
        final List<Subscription> subscriptions = ImmutableList.copyOf(subscriptionsByUser.get(user));
        if (subscriptions != null) {
            return subscriptions.stream()
                .filter(subscription -> subscription.getMailbox().equals(mailbox))
                .findFirst()
                .orElse(null);
        }
        return null;
    }

    @Override
    public List<Subscription> findSubscriptionsForUser(Username user) {
        final List<Subscription> subcriptions = subscriptionsByUser.get(user);
        if (subcriptions == null) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(subcriptions);
    }

    @Override
    public synchronized void save(Subscription subscription) {
        subscriptionsByUser.put(subscription.getUser(), subscription);
    }
    
    public void deleteAll() {
        subscriptionsByUser.clear();
    }

    @Override
    public void endRequest() {
        // nothing to do
    }

}
