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

import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.store.transaction.Mapper;
import org.apache.james.mailbox.store.user.model.Subscription;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Functions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Mapper for {@link Subscription}
 *
 */
public interface SubscriptionMapper extends Mapper {
    /**
     * Saves the given subscription.
     * @param subscription not null
     */
    void save(Subscription subscription) throws SubscriptionException;

    default Mono<Void> saveReactive(Subscription subscription) {
        return Mono.fromRunnable(Throwing.runnable(() -> save(subscription)))
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    /**
     * Finds subscriptions for the given user.
     * @param user not null
     * @return not null
     */
    List<Subscription> findSubscriptionsForUser(Username user) throws SubscriptionException;

    default Flux<Subscription> findSubscriptionsForUserReactive(Username user) {
        return Mono.fromCallable(() -> findSubscriptionsForUser(user))
            .flatMapIterable(Functions.identity())
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Deletes the given subscription.
     * @param subscription not null
     */
    void delete(Subscription subscription) throws SubscriptionException;

    default Mono<Void> deleteReactive(Subscription subscription) {
        return Mono.fromRunnable(Throwing.runnable(() -> delete(subscription)))
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }
}