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

package org.apache.james.jmap.postgres.pushsubscription;

import static org.apache.james.jmap.api.pushsubscription.PushSubscriptionHelpers.evaluateExpiresTime;
import static org.apache.james.jmap.api.pushsubscription.PushSubscriptionHelpers.isInThePast;
import static org.apache.james.jmap.api.pushsubscription.PushSubscriptionHelpers.isInvalidPushSubscriptionKey;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.change.TypeStateFactory;
import org.apache.james.jmap.api.model.DeviceClientIdInvalidException;
import org.apache.james.jmap.api.model.ExpireTimeInvalidException;
import org.apache.james.jmap.api.model.InvalidPushSubscriptionKeys;
import org.apache.james.jmap.api.model.PushSubscription;
import org.apache.james.jmap.api.model.PushSubscriptionCreationRequest;
import org.apache.james.jmap.api.model.PushSubscriptionExpiredTime;
import org.apache.james.jmap.api.model.PushSubscriptionId;
import org.apache.james.jmap.api.model.PushSubscriptionNotFoundException;
import org.apache.james.jmap.api.model.TypeName;
import org.apache.james.jmap.api.pushsubscription.PushSubscriptionRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.OptionConverters;

public class PostgresPushSubscriptionRepository implements PushSubscriptionRepository {
    private final Clock clock;
    private final TypeStateFactory typeStateFactory;
    private final PostgresExecutor.Factory executorFactory;

    @Inject
    @Singleton
    public PostgresPushSubscriptionRepository(Clock clock, TypeStateFactory typeStateFactory, PostgresExecutor.Factory executorFactory) {
        this.clock = clock;
        this.typeStateFactory = typeStateFactory;
        this.executorFactory = executorFactory;
    }

    @Override
    public Mono<PushSubscription> save(Username username, PushSubscriptionCreationRequest request) {
        PushSubscription pushSubscription = PushSubscription.from(request,
            evaluateExpiresTime(OptionConverters.toJava(request.expires().map(PushSubscriptionExpiredTime::value)), clock));

        PostgresPushSubscriptionDAO pushSubscriptionDAO = getDAO(username);
        return pushSubscriptionDAO.existDeviceClientId(username, request.deviceClientId())
            .handle((isDuplicated, sink) -> {
                if (isInThePast(request.expires(), clock)) {
                    sink.error(new ExpireTimeInvalidException(request.expires().get().value(), "expires must be greater than now"));
                    return;
                }
                if (isDuplicated) {
                    sink.error(new DeviceClientIdInvalidException(request.deviceClientId(), "deviceClientId must be unique"));
                    return;
                }
                if (isInvalidPushSubscriptionKey(request.keys())) {
                    sink.error(new InvalidPushSubscriptionKeys(request.keys().get()));
                }
            })
            .then(Mono.defer(() -> pushSubscriptionDAO.save(username, pushSubscription))
                .thenReturn(pushSubscription));
    }

    @Override
    public Mono<PushSubscriptionExpiredTime> updateExpireTime(Username username, PushSubscriptionId id, ZonedDateTime newExpire) {
        return Mono.just(newExpire)
            .handle((inputTime, sink) -> {
                if (newExpire.isBefore(ZonedDateTime.now(clock))) {
                    sink.error(new ExpireTimeInvalidException(inputTime, "expires must be greater than now"));
                }
            })
            .then(getDAO(username).updateExpireTime(username, id, evaluateExpiresTime(Optional.of(newExpire), clock).value())
                .map(PushSubscriptionExpiredTime::new)
                .switchIfEmpty(Mono.error(() -> new PushSubscriptionNotFoundException(id))));
    }

    @Override
    public Mono<Void> updateTypes(Username username, PushSubscriptionId id, Set<TypeName> types) {
        return getDAO(username).updateType(username, id, types)
            .switchIfEmpty(Mono.error(() -> new PushSubscriptionNotFoundException(id)))
            .then();
    }

    @Override
    public Mono<Void> validateVerificationCode(Username username, PushSubscriptionId id) {
        return getDAO(username)
            .updateValidated(username, id, true)
            .switchIfEmpty(Mono.error(() -> new PushSubscriptionNotFoundException(id)))
            .then();
    }

    @Override
    public Mono<Void> revoke(Username username, PushSubscriptionId id) {
        return getDAO(username).deleteByUsernameAndId(username, id);
    }

    @Override
    public Mono<Void> delete(Username username) {
        return getDAO(username).deleteByUsername(username);
    }

    @Override
    public Flux<PushSubscription> get(Username username, Set<PushSubscriptionId> ids) {
        return getDAO(username).getByUsernameAndIds(username, ids);
    }

    @Override
    public Flux<PushSubscription> list(Username username) {
        return getDAO(username).listByUsername(username);
    }

    private PostgresPushSubscriptionDAO getDAO(Username username) {
        return new PostgresPushSubscriptionDAO(executorFactory.create(username.getDomainPart()), typeStateFactory);
    }
}
