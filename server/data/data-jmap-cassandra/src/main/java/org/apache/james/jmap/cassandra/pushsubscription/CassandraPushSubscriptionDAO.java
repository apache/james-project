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

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.apache.james.jmap.cassandra.pushsubscription.tables.CassandraPushSubscriptionTable.DEVICE_CLIENT_ID;
import static org.apache.james.jmap.cassandra.pushsubscription.tables.CassandraPushSubscriptionTable.ENCRYPT_AUTH_SECRET;
import static org.apache.james.jmap.cassandra.pushsubscription.tables.CassandraPushSubscriptionTable.ENCRYPT_PUBLIC_KEY;
import static org.apache.james.jmap.cassandra.pushsubscription.tables.CassandraPushSubscriptionTable.EXPIRES;
import static org.apache.james.jmap.cassandra.pushsubscription.tables.CassandraPushSubscriptionTable.ID;
import static org.apache.james.jmap.cassandra.pushsubscription.tables.CassandraPushSubscriptionTable.TABLE_NAME;
import static org.apache.james.jmap.cassandra.pushsubscription.tables.CassandraPushSubscriptionTable.TYPES;
import static org.apache.james.jmap.cassandra.pushsubscription.tables.CassandraPushSubscriptionTable.URL;
import static org.apache.james.jmap.cassandra.pushsubscription.tables.CassandraPushSubscriptionTable.USER;
import static org.apache.james.jmap.cassandra.pushsubscription.tables.CassandraPushSubscriptionTable.VALIDATED;
import static org.apache.james.jmap.cassandra.pushsubscription.tables.CassandraPushSubscriptionTable.VERIFICATION_CODE;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.change.TypeStateFactory;
import org.apache.james.jmap.api.model.DeviceClientId;
import org.apache.james.jmap.api.model.PushSubscription;
import org.apache.james.jmap.api.model.PushSubscriptionExpiredTime;
import org.apache.james.jmap.api.model.PushSubscriptionId;
import org.apache.james.jmap.api.model.PushSubscriptionKeys;
import org.apache.james.jmap.api.model.PushSubscriptionServerURL;
import org.apache.james.jmap.api.model.TypeName;
import org.apache.james.jmap.api.model.VerificationCode;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.Option;
import scala.collection.immutable.Seq;
import scala.jdk.javaapi.CollectionConverters;
import scala.jdk.javaapi.OptionConverters;

public class CassandraPushSubscriptionDAO {
    private final TypeStateFactory typeStateFactory;
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insert;
    private final PreparedStatement selectAll;
    private final PreparedStatement deleteOne;
    private final PreparedStatement deleteAll;

    @Inject
    public CassandraPushSubscriptionDAO(CqlSession session, TypeStateFactory typeStateFactory) {
        executor = new CassandraAsyncExecutor(session);

        insert = session.prepare(insertInto(TABLE_NAME)
            .value(USER, bindMarker(USER))
            .value(DEVICE_CLIENT_ID, bindMarker(DEVICE_CLIENT_ID))
            .value(ID, bindMarker(ID))
            .value(EXPIRES, bindMarker(EXPIRES))
            .value(TYPES, bindMarker(TYPES))
            .value(URL, bindMarker(URL))
            .value(VERIFICATION_CODE, bindMarker(VERIFICATION_CODE))
            .value(ENCRYPT_PUBLIC_KEY, bindMarker(ENCRYPT_PUBLIC_KEY))
            .value(ENCRYPT_AUTH_SECRET, bindMarker(ENCRYPT_AUTH_SECRET))
            .value(VALIDATED, bindMarker(VALIDATED))
            .build());

        selectAll = session.prepare(selectFrom(TABLE_NAME)
            .all()
            .whereColumn(USER).isEqualTo(bindMarker(USER)).build());

        deleteOne = session.prepare(deleteFrom(TABLE_NAME)
            .whereColumn(USER).isEqualTo(bindMarker(USER))
            .whereColumn(DEVICE_CLIENT_ID).isEqualTo(bindMarker(DEVICE_CLIENT_ID))
            .build());

        deleteAll = session.prepare(deleteFrom(TABLE_NAME)
            .whereColumn(USER).isEqualTo(bindMarker(USER))
            .build());

        this.typeStateFactory = typeStateFactory;
    }

    public Mono<PushSubscription> insert(Username username, PushSubscription subscription) {
        Set<String> typeNames = CollectionConverters.asJava(subscription.types()
            .map(TypeName::asString)
            .toSet());
        Instant utcInstant = subscription.expires().value().withZoneSameInstant(ZoneOffset.UTC).toInstant();

        BoundStatementBuilder insertSubscription = insert.boundStatementBuilder()
            .setString(USER, username.asString())
            .setString(DEVICE_CLIENT_ID, subscription.deviceClientId())
            .setUuid(ID, subscription.id().value())
            .setInstant(EXPIRES, utcInstant)
            .setSet(TYPES, typeNames, String.class)
            .setString(URL, subscription.url().value().toString())
            .setString(VERIFICATION_CODE, subscription.verificationCode())
            .setBoolean(VALIDATED, subscription.validated());

        OptionConverters.toJava(subscription.keys())
            .ifPresent(keys -> insertSubscription.setString(ENCRYPT_PUBLIC_KEY, keys.p256dh())
                .setString(ENCRYPT_AUTH_SECRET, keys.auth()));

        return executor.executeVoid(insertSubscription.build())
            .thenReturn(subscription);
    }

    public Flux<PushSubscription> selectAll(Username username) {
        return executor.executeRows(selectAll.bind().setString(USER, username.asString()))
            .map(this::toPushSubscription);
    }

    public Mono<Void> deleteOne(Username username, String deviceClientId) {
        return executor.executeVoid(deleteOne.bind()
            .setString(USER, username.asString())
            .setString(DEVICE_CLIENT_ID, deviceClientId));
    }

    public Mono<Void> deleteAll(Username username) {
        return executor.executeVoid(deleteAll.bind()
            .setString(USER, username.asString()));
    }

    private PushSubscription toPushSubscription(Row row) {
        return PushSubscription.apply(
            PushSubscriptionId.apply(row.getUuid(ID)),
            DeviceClientId.apply(row.getString(DEVICE_CLIENT_ID)),
            PushSubscriptionServerURL.from(row.getString(URL)).get(),
            toKeys(row),
            VerificationCode.apply(row.getString(VERIFICATION_CODE)),
            row.getBoolean(VALIDATED),
            toExpires(row),
            toTypes(row));
    }

    private Option<PushSubscriptionKeys> toKeys(Row row) {
        String p256dh = row.getString(ENCRYPT_PUBLIC_KEY);
        String auth = row.getString(ENCRYPT_AUTH_SECRET);
        if (p256dh == null && auth == null) {
            return Option.empty();
        } else {
            return Option.apply(PushSubscriptionKeys.apply(p256dh, auth));
        }
    }

    private PushSubscriptionExpiredTime toExpires(Row row) {
        return PushSubscriptionExpiredTime.apply(
            ZonedDateTime.ofInstant(row.getInstant(EXPIRES), ZoneOffset.UTC));
    }

    private Seq<TypeName> toTypes(Row row) {
        return CollectionConverters.asScala(row.getSet(TYPES, String.class).stream()
                .map(string -> typeStateFactory.parse(string).right().get())
                .collect(ImmutableSet.toImmutableSet()))
            .toSeq();
    }
}