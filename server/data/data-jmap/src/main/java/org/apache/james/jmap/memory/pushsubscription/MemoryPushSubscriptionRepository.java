/******************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one     *
 * or more contributor license agreements.  See the NOTICE file   *
 * distributed with this work for additional information          *
 * regarding copyright ownership.  The ASF licenses this file     *
 * to you under the Apache License, Version 2.0 (the              *
 * "License"); you may not use this file except in compliance     *
 * with the License.  You may obtain a copy of the License at     *
 *                                                                *
 * http://www.apache.org/licenses/LICENSE-2.0                     *
 *                                                                *
 * Unless required by applicable law or agreed to in writing,     *
 * software distributed under the License is distributed on an    *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY         *
 * KIND, either express or implied.  See the License for the      *
 * specific language governing permissions and limitations        *
 * under the License.                                             *
 ******************************************************************/

package org.apache.james.jmap.memory.pushsubscription;

import static org.apache.james.jmap.api.pushsubscription.PushSubscriptionHelpers.evaluateExpiresTime;
import static org.apache.james.jmap.api.pushsubscription.PushSubscriptionHelpers.isInThePast;
import static org.apache.james.jmap.api.pushsubscription.PushSubscriptionHelpers.isInvalidPushSubscriptionKey;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

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

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.CollectionConverters;
import scala.jdk.javaapi.OptionConverters;

public class MemoryPushSubscriptionRepository implements PushSubscriptionRepository {
    private final Table<Username, PushSubscriptionId, PushSubscription> table;
    private final Clock clock;

    @Inject
    public MemoryPushSubscriptionRepository(Clock clock) {
        this.clock = clock;
        this.table = HashBasedTable.create();
    }

    @Override
    public Publisher<PushSubscription> save(Username username, PushSubscriptionCreationRequest request) {
        return Mono.just(request)
            .handle((req, sink) -> {
                if (isInThePast(req.expires(), clock)) {
                    sink.error(new ExpireTimeInvalidException(req.expires().get().value(), "expires must be greater than now"));
                }
                if (!isUniqueDeviceClientId(username, req.deviceClientId())) {
                    sink.error(new DeviceClientIdInvalidException(req.deviceClientId(), "deviceClientId must be unique"));
                }
                if (isInvalidPushSubscriptionKey(req.keys())) {
                    sink.error(new InvalidPushSubscriptionKeys(req.keys().get()));
                }
            })
            .thenReturn(PushSubscription.from(request,
                evaluateExpiresTime(OptionConverters.toJava(request.expires().map(PushSubscriptionExpiredTime::value)),
                    clock)))
            .doOnNext(pushSubscription -> table.put(username, pushSubscription.id(), pushSubscription));
    }

    @Override
    public Publisher<PushSubscriptionExpiredTime> updateExpireTime(Username username, PushSubscriptionId id, ZonedDateTime newExpire) {
        return Mono.just(newExpire)
            .handle((inputTime, sink) -> {
                if (newExpire.isBefore(ZonedDateTime.now(clock))) {
                    sink.error(new ExpireTimeInvalidException(inputTime, "expires must be greater than now"));
                }
            })
            .then(Mono.justOrEmpty(table.get(username, id))
                .mapNotNull(pushSubscription -> {
                    PushSubscription value = pushSubscription.withExpires(evaluateExpiresTime(Optional.of(newExpire), clock));
                    table.put(username, id, value);
                    return value;
                })
                .map(PushSubscription::expires)
                .switchIfEmpty(Mono.error(() -> new PushSubscriptionNotFoundException(id))));
    }

    @Override
    public Publisher<Void> updateTypes(Username username, PushSubscriptionId id, Set<TypeName> types) {
        return Mono.justOrEmpty(table.get(username, id))
            .doOnNext(pushSubscription -> {
                PushSubscription newPushSubscription = pushSubscription.withTypes(CollectionConverters.asScala(types).toSeq());
                table.put(username, id, newPushSubscription);
            })
            .switchIfEmpty(Mono.error(() -> new PushSubscriptionNotFoundException(id)))
            .then();
    }

    @Override
    public Publisher<Void> revoke(Username username, PushSubscriptionId id) {
        return Mono.fromCallable(() -> table.remove(username, id)).then();
    }

    @Override
    public Publisher<Void> delete(Username username) {
        return Mono.fromCallable(() -> table.rowMap().remove(username)).then();
    }

    @Override
    public Publisher<PushSubscription> get(Username username, Set<PushSubscriptionId> ids) {
        return Flux.fromStream(table.row(username).entrySet().stream())
            .filter(entry -> ids.contains(entry.getKey()))
            .map(Map.Entry::getValue);
    }

    @Override
    public Publisher<PushSubscription> list(Username username) {
        return Flux.fromStream(table.row(username).entrySet().stream())
            .map(Map.Entry::getValue);
    }

    @Override
    public Publisher<Void> validateVerificationCode(Username username, PushSubscriptionId id) {
        return Mono.justOrEmpty(table.get(username, id))
            .doOnNext(pushSubscription -> {
                if (!pushSubscription.validated()) {
                    PushSubscription newPushSubscription = pushSubscription.verified();
                    table.put(username, id, newPushSubscription);
                }
            })
            .switchIfEmpty(Mono.error(() -> new PushSubscriptionNotFoundException(id)))
            .then();
    }

    private boolean isUniqueDeviceClientId(Username username, String deviceClientId) {
        return table.row(username).values().stream()
            .noneMatch(subscription -> subscription.deviceClientId().equals(deviceClientId));
    }

}
