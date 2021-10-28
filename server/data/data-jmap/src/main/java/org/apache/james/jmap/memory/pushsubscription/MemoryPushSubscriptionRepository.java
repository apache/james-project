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

import static org.apache.james.jmap.api.model.PushSubscription.EXPIRES_TIME_MAX_DAY;

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
import org.apache.james.jmap.api.model.PushSubscriptionKeys;
import org.apache.james.jmap.api.model.PushSubscriptionNotFoundException;
import org.apache.james.jmap.api.model.TypeName;
import org.apache.james.jmap.api.pushsubscription.PushSubscriptionRepository;
import org.reactivestreams.Publisher;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.Option;
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
                if (isInThePast(req.expires())) {
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
                evaluateExpiresTime(OptionConverters.toJava(request.expires().map(PushSubscriptionExpiredTime::value)))))
            .doOnNext(pushSubscription -> table.put(username, pushSubscription.id(), pushSubscription));
    }

    @Override
    public Publisher<Void> updateExpireTime(Username username, PushSubscriptionId id, ZonedDateTime newExpire) {
        return Mono.just(newExpire)
            .handle((inputTime, sink) -> {
                if (newExpire.isBefore(ZonedDateTime.now(clock))) {
                    sink.error(new ExpireTimeInvalidException(inputTime, "expires must be greater than now"));
                }
            })
            .then(Mono.justOrEmpty(table.get(username, id))
                .doOnNext(pushSubscription -> table.put(username, id,
                    pushSubscription.withExpires(evaluateExpiresTime(Optional.of(newExpire)))))
                .switchIfEmpty(Mono.error(() -> new PushSubscriptionNotFoundException(id)))
                .then());
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
    public Publisher<PushSubscription> get(Username username, Set<PushSubscriptionId> ids) {
        return Flux.fromStream(table.row(username).entrySet().stream())
            .filter(entry -> ids.contains(entry.getKey()))
            .map(Map.Entry::getValue)
            .filter(subscription -> isNotOutdatedSubscription(subscription, clock));
    }

    @Override
    public Publisher<PushSubscription> list(Username username) {
        return Flux.fromStream(table.row(username).entrySet().stream())
            .map(Map.Entry::getValue)
            .filter(subscription -> isNotOutdatedSubscription(subscription, clock));
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

    private boolean isInThePast(PushSubscriptionExpiredTime expire) {
        return expire.isBefore(ZonedDateTime.now(clock));
    }

    private boolean isInThePast(Option<PushSubscriptionExpiredTime> expire) {
        return expire.map(this::isInThePast).getOrElse(() -> false);
    }

    private PushSubscriptionExpiredTime evaluateExpiresTime(Optional<ZonedDateTime> inputTime) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        ZonedDateTime maxExpiresTime = now.plusDays(EXPIRES_TIME_MAX_DAY());
        return PushSubscriptionExpiredTime.apply(inputTime.filter(input -> input.isBefore(maxExpiresTime))
            .orElse(maxExpiresTime));
    }

    private boolean isNotOutdatedSubscription(PushSubscription subscription, Clock clock) {
        return subscription.expires().isAfter(ZonedDateTime.now(clock));
    }

    private boolean isUniqueDeviceClientId(Username username, String deviceClientId) {
        return table.row(username).values().stream()
            .noneMatch(subscription -> subscription.deviceClientId().equals(deviceClientId));
    }

    private boolean isInvalidPushSubscriptionKey(Option<PushSubscriptionKeys> keysOption) {
        return OptionConverters.toJava(keysOption)
            .map(key -> key.p256dh().isEmpty() || key.auth().isEmpty())
            .orElse(false);
    }
}
