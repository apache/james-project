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
package org.apache.james.rrt.lib;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.rrt.api.AliasReverseResolver;
import org.apache.james.rrt.api.CanSendFrom;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CanSendFromImpl implements CanSendFrom {

    @FunctionalInterface
    interface DomainFetcher {
        Flux<Domain> fetch(Domain user);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(CanSendFromImpl.class);
    private final AliasReverseResolver aliasReverseResolver;

    @Inject
    public CanSendFromImpl(AliasReverseResolver aliasReverseResolver) {
        this.aliasReverseResolver = aliasReverseResolver;
    }

    @Override
    public boolean userCanSendFrom(Username connectedUser, Username fromUser) {
        try {
            return connectedUser.equals(fromUser) ||  allValidFromAddressesForUser(connectedUser)
                .map(Username::fromMailAddress)
                .any(fromUser::equals)
                .block();
        } catch (Exception e) {
            LOGGER.warn("Error upon {} mapping resolution for {}. You might want to audit mapping content for this mapping entry. ",
                fromUser.asString(),
                connectedUser.asString());
            return false;
        }
    }

    @Override
    public Publisher<Boolean> userCanSendFromReactive(Username connectedUser, Username fromUser) {
        if (connectedUser.equals(fromUser)) {
            return Mono.just(true);
        }
        return allValidFromAddressesForUser(connectedUser)
            .map(Username::fromMailAddress)
            .any(fromUser::equals);
    }

    @Override
    public Flux<MailAddress> allValidFromAddressesForUser(Username user) {
        return Flux.from(aliasReverseResolver.listAddresses(user));
    }
}
