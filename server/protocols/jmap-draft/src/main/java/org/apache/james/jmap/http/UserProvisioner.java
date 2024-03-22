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
package org.apache.james.jmap.http;

import java.util.UUID;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.JMAPConfiguration;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.user.api.AlreadyExistInUsersRepositoryException;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.util.FunctionalUtils;
import org.apache.james.util.ReactorUtils;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Mono;

public class UserProvisioner {
    private final JMAPConfiguration jmapConfiguration;
    private final UsersRepository usersRepository;
    private final MetricFactory metricFactory;

    @Inject
    @VisibleForTesting
    UserProvisioner(JMAPConfiguration jmapConfiguration, UsersRepository usersRepository, MetricFactory metricFactory) {
        this.jmapConfiguration = jmapConfiguration;
        this.usersRepository = usersRepository;
        this.metricFactory = metricFactory;
    }

    public Mono<Void> provisionUser(MailboxSession session) {
        if (session != null && !usersRepository.isReadOnly() && jmapConfiguration.isUserProvisioningEnabled()) {
            return createAccountIfNeeded(session);
        }
        return Mono.empty();
    }

    private Mono<Void> createAccountIfNeeded(MailboxSession session) {
        Username username = session.getUser();
        return Mono.from(metricFactory.decoratePublisherWithTimerMetric("JMAP-user-provisioning",
            needsAccountCreation(username)
                .filter(FunctionalUtils.identityPredicate())
                .flatMap(any -> createAccount(username))
                .onErrorResume(AlreadyExistInUsersRepositoryException.class, e -> Mono.empty())));
    }

    private Mono<Void> createAccount(Username username) {
        return Mono.fromRunnable(Throwing.runnable(() -> usersRepository.addUser(username, generatePassword())))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
            .then();
    }

    private Mono<Boolean> needsAccountCreation(Username username) {
        return Mono.from(usersRepository.containsReactive(username))
            .map(FunctionalUtils.negate());
    }

    private String generatePassword() {
        return UUID.randomUUID().toString();
    }
}
