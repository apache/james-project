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

import static org.apache.james.backends.postgres.PostgresCommons.IN_CLAUSE_MAX_SIZE;
import static org.apache.james.backends.postgres.PostgresCommons.LOCAL_DATE_TIME_ZONED_DATE_TIME_FUNCTION;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.change.TypeStateFactory;
import org.apache.james.jmap.api.model.PushSubscription;
import org.apache.james.jmap.api.model.PushSubscriptionExpiredTime;
import org.apache.james.jmap.api.model.PushSubscriptionId;
import org.apache.james.jmap.api.model.PushSubscriptionKeys;
import org.apache.james.jmap.api.model.PushSubscriptionServerURL;
import org.apache.james.jmap.api.model.TypeName;
import org.apache.james.jmap.postgres.pushsubscription.PostgresPushSubscriptionModule.PushSubscriptionTable;
import org.jooq.Record;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.CollectionConverters;
import scala.jdk.javaapi.OptionConverters;

public class PostgresPushSubscriptionDAO {
    private final PostgresExecutor postgresExecutor;
    private final TypeStateFactory typeStateFactory;

    public PostgresPushSubscriptionDAO(PostgresExecutor postgresExecutor, TypeStateFactory typeStateFactory) {
        this.postgresExecutor = postgresExecutor;
        this.typeStateFactory = typeStateFactory;
    }

    public Mono<Void> save(Username username, PushSubscription pushSubscription) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(PushSubscriptionTable.TABLE_NAME)
            .set(PushSubscriptionTable.USER, username.asString())
            .set(PushSubscriptionTable.DEVICE_CLIENT_ID, pushSubscription.deviceClientId())
            .set(PushSubscriptionTable.ID, pushSubscription.id().value())
            .set(PushSubscriptionTable.EXPIRES, pushSubscription.expires().value().toLocalDateTime())
            .set(PushSubscriptionTable.TYPES, CollectionConverters.asJava(pushSubscription.types())
                .stream().map(TypeName::asString).toArray(String[]::new))
            .set(PushSubscriptionTable.URL, pushSubscription.url().value().toString())
            .set(PushSubscriptionTable.VERIFICATION_CODE, pushSubscription.verificationCode())
            .set(PushSubscriptionTable.VALIDATED, pushSubscription.validated())
            .set(PushSubscriptionTable.ENCRYPT_PUBLIC_KEY, OptionConverters.toJava(pushSubscription.keys().map(PushSubscriptionKeys::p256dh)).orElse(null))
            .set(PushSubscriptionTable.ENCRYPT_AUTH_SECRET, OptionConverters.toJava(pushSubscription.keys().map(PushSubscriptionKeys::auth)).orElse(null))));
    }

    public Flux<PushSubscription> listByUsername(Username username) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.selectFrom(PushSubscriptionTable.TABLE_NAME)
                .where(PushSubscriptionTable.USER.eq(username.asString()))))
            .map(this::recordAsPushSubscription);
    }

    public Flux<PushSubscription> getByUsernameAndIds(Username username, Collection<PushSubscriptionId> ids) {
        Function<Collection<PushSubscriptionId>, Flux<PushSubscription>> queryPublisherFunction = idsMatching -> postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.selectFrom(PushSubscriptionTable.TABLE_NAME)
                .where(PushSubscriptionTable.USER.eq(username.asString()))
                .and(PushSubscriptionTable.ID.in(idsMatching.stream().map(PushSubscriptionId::value).collect(Collectors.toList())))))
            .map(this::recordAsPushSubscription);

        if (ids.size() <= IN_CLAUSE_MAX_SIZE) {
            return queryPublisherFunction.apply(ids);
        } else {
            return Flux.fromIterable(Iterables.partition(ids, IN_CLAUSE_MAX_SIZE))
                .flatMap(queryPublisherFunction);
        }
    }

    public Mono<Void> deleteByUsername(Username username) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(PushSubscriptionTable.TABLE_NAME)
            .where(PushSubscriptionTable.USER.eq(username.asString()))));
    }

    public Mono<Void> deleteByUsernameAndId(Username username, PushSubscriptionId id) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(PushSubscriptionTable.TABLE_NAME)
            .where(PushSubscriptionTable.USER.eq(username.asString()))
            .and(PushSubscriptionTable.ID.eq(id.value()))));
    }

    public Mono<Set<TypeName>> updateType(Username username, PushSubscriptionId id, Set<TypeName> newTypes) {
        Preconditions.checkNotNull(newTypes, "newTypes should not be null");
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.update(PushSubscriptionTable.TABLE_NAME)
            .set(PushSubscriptionTable.TYPES, newTypes.stream().map(TypeName::asString).toArray(String[]::new))
            .where(PushSubscriptionTable.USER.eq(username.asString()))
            .and(PushSubscriptionTable.ID.eq(id.value()))
            .returning(PushSubscriptionTable.TYPES)))
            .map(this::extractTypes);
    }

    public Mono<Boolean> updateValidated(Username username, PushSubscriptionId id, boolean validated) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.update(PushSubscriptionTable.TABLE_NAME)
            .set(PushSubscriptionTable.VALIDATED, validated)
            .where(PushSubscriptionTable.USER.eq(username.asString()))
            .and(PushSubscriptionTable.ID.eq(id.value()))
            .returning(PushSubscriptionTable.VALIDATED)))
            .map(record -> record.get(PushSubscriptionTable.VALIDATED));
    }

    public Mono<ZonedDateTime> updateExpireTime(Username username, PushSubscriptionId id, ZonedDateTime newExpire) {
        Preconditions.checkNotNull(newExpire, "newExpire should not be null");
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.update(PushSubscriptionTable.TABLE_NAME)
                .set(PushSubscriptionTable.EXPIRES, newExpire.toLocalDateTime())
                .where(PushSubscriptionTable.USER.eq(username.asString()))
                .and(PushSubscriptionTable.ID.eq(id.value()))
                .returning(PushSubscriptionTable.EXPIRES)))
            .map(record -> LOCAL_DATE_TIME_ZONED_DATE_TIME_FUNCTION.apply(record.get(PushSubscriptionTable.EXPIRES)));
    }

    public Mono<Boolean> existDeviceClientId(Username username, String deviceClientId) {
        return postgresExecutor.executeExists(dslContext -> dslContext.selectOne()
            .from(PushSubscriptionTable.TABLE_NAME)
            .where(PushSubscriptionTable.USER.eq(username.asString()))
            .and(PushSubscriptionTable.DEVICE_CLIENT_ID.eq(deviceClientId)));
    }

    private PushSubscription recordAsPushSubscription(Record record) {
        try {
            return new PushSubscription(new PushSubscriptionId(record.get(PushSubscriptionTable.ID)),
                record.get(PushSubscriptionTable.DEVICE_CLIENT_ID),
                PushSubscriptionServerURL.from(record.get(PushSubscriptionTable.URL)).get(),
                scala.jdk.javaapi.OptionConverters.toScala(Optional.ofNullable(record.get(PushSubscriptionTable.ENCRYPT_PUBLIC_KEY))
                    .flatMap(key -> Optional.ofNullable(record.get(PushSubscriptionTable.ENCRYPT_AUTH_SECRET))
                        .map(secret -> new PushSubscriptionKeys(key, secret)))),
                record.get(PushSubscriptionTable.VERIFICATION_CODE),
                record.get(PushSubscriptionTable.VALIDATED),
                Optional.ofNullable(record.get(PushSubscriptionTable.EXPIRES, LocalDateTime.class))
                    .map(LOCAL_DATE_TIME_ZONED_DATE_TIME_FUNCTION)
                    .map(PushSubscriptionExpiredTime::new).get(),
                CollectionConverters.asScala(extractTypes(record)).toSeq());
        } catch (Exception e) {
            throw new RuntimeException("Error while parsing PushSubscription from database", e);
        }
    }

    private Set<TypeName> extractTypes(Record record) {
        return  Arrays.stream(record.get(PushSubscriptionTable.TYPES))
            .map(string -> typeStateFactory.parse(string).right().get())
            .collect(Collectors.toSet());
    }
}