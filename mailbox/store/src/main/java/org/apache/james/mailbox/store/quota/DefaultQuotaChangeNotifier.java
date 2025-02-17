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

package org.apache.james.mailbox.store.quota;

import java.time.Instant;
import java.util.function.Supplier;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.events.EventBus;
import org.apache.james.events.RegistrationKey;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.QuotaChangeNotifier;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.mailbox.store.event.EventFactory;
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DefaultQuotaChangeNotifier implements QuotaChangeNotifier {
    @FunctionalInterface
    public interface UsernameSupplier extends Supplier<Flux<Username>> {

    }

    private static final ImmutableSet<RegistrationKey> NO_REGISTRATION_KEYS = ImmutableSet.of();

    private final UsernameSupplier usernameSupplier;
    private final UserQuotaRootResolver quotaRootResolver;
    private final EventBus eventBus;
    private final QuotaManager quotaManager;

    @Inject
    public DefaultQuotaChangeNotifier(UsernameSupplier usernameSupplier, UserQuotaRootResolver quotaRootResolver,
                                      EventBus eventBus, QuotaManager quotaManager) {
        this.usernameSupplier = usernameSupplier;
        this.quotaRootResolver = quotaRootResolver;
        this.eventBus = eventBus;
        this.quotaManager = quotaManager;
    }

    @Override
    public Publisher<Void> notifyUpdate(QuotaRoot quotaRoot) {
        Username username = quotaRootResolver.associatedUsername(quotaRoot);
        return Mono.from(quotaManager.getQuotasReactive(quotaRoot))
            .flatMap(quotas -> eventBus.dispatch(
                EventFactory.quotaUpdated()
                    .randomEventId()
                    .user(username)
                    .quotaRoot(quotaRoot)
                    .quotaCount(quotas.getMessageQuota())
                    .quotaSize(quotas.getStorageQuota())
                    .instant(Instant.now())
                    .build(),
                NO_REGISTRATION_KEYS));
    }

    @Override
    public Publisher<Void> notifyUpdate(Domain domain) {
        return usernameSupplier.get()
            .map(quotaRootResolver::forUser)
            .filter(user -> user.getDomain().map(domain::equals).orElse(false))
            .concatMap(this::notifyUpdate)
            .then();
    }

    @Override
    public Publisher<Void> notifyGlobalUpdate() {
        return usernameSupplier.get()
            .map(quotaRootResolver::forUser)
            .concatMap(this::notifyUpdate)
            .then();
    }
}
