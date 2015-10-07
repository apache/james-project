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
package org.apache.james.mailbox.store.user;

import java.util.List;

import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.store.transaction.Mapper;
import org.apache.james.mailbox.store.user.model.Subscription;

/**
 * Mapper for {@link Subscription}
 *
 */
public interface SubscriptionMapper extends Mapper {

	
    /**
     * Finds any subscriptions for a given user to the given mailbox.
     * @param user not null
     * @param mailbox not null
     * @return <code>Subscription</code>, 
     * or null when the user is not subscribed to the given mailbox
     */
    public abstract Subscription findMailboxSubscriptionForUser(
            final String user, final String mailbox) throws SubscriptionException;

    /**
     * Saves the given subscription.
     * @param subscription not null
     */
    public abstract void save(Subscription subscription) throws SubscriptionException;

    /**
     * Finds subscriptions for the given user.
     * @param user not null
     * @return not null
     */
    public abstract List<Subscription> findSubscriptionsForUser(String user) throws SubscriptionException;

    /**
     * Deletes the given subscription.
     * @param subscription not null
     */
    public abstract void delete(Subscription subscription) throws SubscriptionException;
}