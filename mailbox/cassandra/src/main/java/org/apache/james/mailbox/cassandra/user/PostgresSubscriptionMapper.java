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

package org.apache.james.mailbox.cassandra.user;

import java.util.List;

import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.store.user.SubscriptionMapper;
import org.apache.james.mailbox.store.user.model.Subscription;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Result;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresSubscriptionMapper implements SubscriptionMapper {

    private final Mono<Connection> connectionPublisher;

    public PostgresSubscriptionMapper(Mono<Connection> connectionPublisher) {
        this.connectionPublisher = connectionPublisher;
    }

    @Override
    public void save(Subscription subscription) throws SubscriptionException {
        saveReactive(subscription).block();
    }

    @Override
    public List<Subscription> findSubscriptionsForUser(Username user) throws SubscriptionException {
        return findSubscriptionsForUserReactive(user).collectList().block();
    }

    @Override
    public void delete(Subscription subscription) throws SubscriptionException {
        deleteReactive(subscription).block();
    }

    @Override
    public Mono<Void> saveReactive(Subscription subscription) {
        return connectionPublisher
            .flatMapMany(c -> c.createStatement("INSERT INTO subscription (username, mailbox) VALUES ($1, $2) " +
                    "ON CONFLICT (username, mailbox) DO NOTHING")
                .bind("$1", subscription.getUser().asString())
                .bind("$2", subscription.getMailbox())
                .execute())
            .flatMap(Result::getRowsUpdated)
            .collectList()
            .then();
    }

    @Override
    public Flux<Subscription> findSubscriptionsForUserReactive(Username user) {
        return connectionPublisher
            .flatMapMany(c -> c.createStatement("SELECT * FROM subscription WHERE username = $1")
                .bind("$1", user.asString())
                .execute())
            .flatMap(result -> result.map((row, rowMetadata) -> new Subscription(user, row.get("mailbox", String.class))));
    }

    @Override
    public Mono<Void> deleteReactive(Subscription subscription) {
        return connectionPublisher
            .flatMapMany(c -> c.createStatement("DELETE FROM subscription WHERE username = $1 AND mailbox = $2")
                .bind("$1", subscription.getUser().asString())
                .bind("$2", subscription.getMailbox())
                .execute())
            .flatMap(Result::getRowsUpdated)
            .then();
    }

}
