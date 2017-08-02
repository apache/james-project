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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.james.mailbox.store.transaction.NonTransactionalMapper;
import org.apache.james.mailbox.store.user.SubscriptionMapper;
import org.apache.james.mailbox.store.user.model.Subscription;

import com.google.common.collect.ImmutableList;

public class InMemorySubscriptionMapper extends NonTransactionalMapper implements SubscriptionMapper {
    
    private static final int INITIAL_SIZE = 64;
    private final Map<String, List<Subscription>> subscriptionsByUser;
    
    public InMemorySubscriptionMapper() {
        subscriptionsByUser = new ConcurrentHashMap<>(INITIAL_SIZE);
    }

    public synchronized void delete(Subscription subscription) {
        final String user = subscription.getUser();
        final List<Subscription> subscriptions = subscriptionsByUser.get(user);
        if (subscriptions != null) {
            subscriptions.remove(subscription);
        }
    }

    public Subscription findMailboxSubscriptionForUser(String user, String mailbox) {
        final List<Subscription> subscriptions = subscriptionsByUser.get(user);
        if (subscriptions != null) {
            return subscriptions.stream()
                .filter(subscription -> subscription.getMailbox().equals(mailbox))
                .findFirst()
                .orElse(null);
        }
        return null;
    }

    public List<Subscription> findSubscriptionsForUser(String user) {
        final List<Subscription> subcriptions = subscriptionsByUser.get(user);
        final List<Subscription> results;
        if (subcriptions == null) {
            results = ImmutableList.of();
        } else {
            results = ImmutableList.copyOf(subcriptions);
        }
        return results;
    }

    public synchronized void save(Subscription subscription) {
        final String user = subscription.getUser();
        final List<Subscription> subscriptions = subscriptionsByUser.get(user);
        if (subscriptions == null) {
            final List<Subscription> newSubscriptions  = new ArrayList<>();
            newSubscriptions.add(subscription);
            subscriptionsByUser.put(user, newSubscriptions);
        } else {
            subscriptions.add(subscription);
        }
    }
    
    public void deleteAll() {
        subscriptionsByUser.clear();
    }

    public void endRequest() {
        // nothing to do
    }

}
