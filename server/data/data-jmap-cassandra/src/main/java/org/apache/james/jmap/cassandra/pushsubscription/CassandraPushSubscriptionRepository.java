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

package org.apache.james.jmap.cassandra.pushsubscription;

import static org.apache.james.jmap.api.pushsubscription.PushSubscriptionHelpers.evaluateExpiresTime;
import static org.apache.james.jmap.api.pushsubscription.PushSubscriptionHelpers.isInThePast;
import static org.apache.james.jmap.api.pushsubscription.PushSubscriptionHelpers.isInvalidPushSubscriptionKey;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
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
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;
import scala.jdk.javaapi.CollectionConverters;
import scala.jdk.javaapi.OptionConverters;

public class CassandraPushSubscriptionRepository implements PushSubscriptionRepository {
    private final CassandraPushSubscriptionDAO dao;
    private final Clock clock;

    @Inject
    public CassandraPushSubscriptionRepository(CassandraPushSubscriptionDAO dao, Clock clock) {
        this.dao = dao;
        this.clock = clock;
    }

    @Override
    public Publisher<PushSubscription> save(Username username, PushSubscriptionCreationRequest request) {
        PushSubscription pushSubscription = PushSubscription.from(request,
            evaluateExpiresTime(OptionConverters.toJava(request.expires().map(PushSubscriptionExpiredTime::value)), clock));

        return isDuplicatedDeviceClientId(username, request.deviceClientId())
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
                    return;
                }
            })
            .then(dao.insert(username, pushSubscription))
            .thenReturn(pushSubscription);
    }

    @Override
    public Publisher<PushSubscriptionExpiredTime> updateExpireTime(Username username, PushSubscriptionId id, ZonedDateTime newExpire) {
        return Mono.just(newExpire)
            .handle((inputTime, sink) -> {
                if (newExpire.isBefore(ZonedDateTime.now(clock))) {
                    sink.error(new ExpireTimeInvalidException(inputTime, "expires must be greater than now"));
                }
            })
            .then(retrieveByPushSubscriptionId(username, id)
                .flatMap(subscription -> dao.insert(username,
                    subscription.withExpires(evaluateExpiresTime(Optional.of(newExpire), clock))))
                .map(PushSubscription::expires)
                .switchIfEmpty(Mono.error(() -> new PushSubscriptionNotFoundException(id))));
    }

    @Override
    public Publisher<Void> updateTypes(Username username, PushSubscriptionId id, Set<TypeName> types) {
        return retrieveByPushSubscriptionId(username, id)
            .map(subscription -> subscription.withTypes(CollectionConverters.asScala(types).toSeq()))
            .flatMap(newPushSubscription -> dao.insert(username, newPushSubscription))
            .switchIfEmpty(Mono.error(() -> new PushSubscriptionNotFoundException(id)))
            .then();
    }

    @Override
    public Publisher<Void> validateVerificationCode(Username username, PushSubscriptionId id) {
        return retrieveByPushSubscriptionId(username, id)
            .map(PushSubscription::verified)
            .flatMap(newPushSubscription -> dao.insert(username, newPushSubscription))
            .switchIfEmpty(Mono.error(() -> new PushSubscriptionNotFoundException(id)))
            .then();
    }

    @Override
    public Publisher<Void> revoke(Username username, PushSubscriptionId id) {
        return Mono.from(retrieveByPushSubscriptionId(username, id))
            .flatMap(subscription -> dao.deleteOne(username, subscription.deviceClientId()))
            .switchIfEmpty(Mono.empty());
    }

    @Override
    public Publisher<Void> delete(Username username) {
        return dao.deleteAll(username);
    }

    @Override
    public Publisher<PushSubscription> get(Username username, Set<PushSubscriptionId> ids) {
        return dao.selectAll(username)
            .filter(subscription -> ids.contains(subscription.id()));
    }

    @Override
    public Publisher<PushSubscription> list(Username username) {
        return dao.selectAll(username);
    }

    private Mono<PushSubscription> retrieveByPushSubscriptionId(Username username, PushSubscriptionId id) {
        return dao.selectAll(username).filter(subscription -> subscription.id().equals(id)).next();
    }

    private Mono<Boolean> isDuplicatedDeviceClientId(Username username, String deviceClientId) {
        return dao.selectAll(username)
            .filter(subscription -> subscription.deviceClientId().equals(deviceClientId))
            .hasElements();
    }

}