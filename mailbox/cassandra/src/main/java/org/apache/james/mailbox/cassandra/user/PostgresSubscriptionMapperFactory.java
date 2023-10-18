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

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.store.user.SubscriptionMapperFactory;

import com.google.common.base.Preconditions;

import io.r2dbc.spi.Connection;
import reactor.core.publisher.Mono;

public class PostgresSubscriptionMapperFactory implements SubscriptionMapperFactory {

    private final PostgresConnectionResolver connectionResolver;

    public PostgresSubscriptionMapperFactory(PostgresConnectionResolver connectionResolver) {
        this.connectionResolver = connectionResolver;
    }

    @Override
    public PostgresSubscriptionMapper getSubscriptionMapper(MailboxSession session) {
        return getSubscriptionMapperReactive(session)
            .block();
    }

    public Mono<PostgresSubscriptionMapper> getSubscriptionMapperReactive(MailboxSession session) {
        Preconditions.checkState(session.getUser().hasDomainPart(), "Username %s should have a domain part", session.getUser());

        return Mono.just(Mono.from(connectionResolver.resolver(session))
                .map(connection -> (Connection) connection))
            .map(PostgresSubscriptionMapper::new);
    }

    public Mono<Void> endProcessingRequest(MailboxSession session) {
        Preconditions.checkState(session.getUser().hasDomainPart(), "Username %s should have a domain part", session.getUser());

        return Mono.from(connectionResolver.release(session));
    }
}
