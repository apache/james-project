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
import java.util.Set;

import org.apache.james.core.Username;
import org.apache.james.mailbox.store.transaction.NonTransactionalMapper;
import org.apache.james.mailbox.store.user.SubscriptionMapper;
import org.apache.james.mailbox.store.user.model.Subscription;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

public class InMemorySubscriptionMapper extends NonTransactionalMapper implements SubscriptionMapper {
    private enum ValueHolder {
        INSTANCE
    }
    
    private final Table<Username, String, ValueHolder> subscriptionsByUser;
    
    public InMemorySubscriptionMapper() {
        subscriptionsByUser = HashBasedTable.create();
    }

    @Override
    public void delete(Subscription subscription) {
        synchronized (subscriptionsByUser) {
            subscriptionsByUser.remove(subscription.getUser(), subscription.getMailbox());
        }
    }

    @Override
    public List<Subscription> findSubscriptionsForUser(Username user) {
        synchronized (subscriptionsByUser) {
            Set<String> subscriptions = subscriptionsByUser.row(user).keySet();

            return subscriptions.stream()
                .map(mailbox -> new Subscription(user, mailbox))
                .collect(Guavate.toImmutableList());
        }
    }

    @Override
    public void save(Subscription subscription) {
        synchronized (subscriptionsByUser) {
            subscriptionsByUser.put(subscription.getUser(), subscription.getMailbox(), ValueHolder.INSTANCE);
        }
    }
    
    public void deleteAll() {
        synchronized (subscriptionsByUser) {
            subscriptionsByUser.clear();
        }
    }

    @Override
    public void endRequest() {
        // nothing to do
    }

}
