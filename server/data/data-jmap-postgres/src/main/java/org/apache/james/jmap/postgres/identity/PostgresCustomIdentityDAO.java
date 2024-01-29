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

package org.apache.james.jmap.postgres.identity;

import static org.apache.james.jmap.postgres.identity.PostgresCustomIdentityModule.PostgresCustomIdentityTable.BCC;
import static org.apache.james.jmap.postgres.identity.PostgresCustomIdentityModule.PostgresCustomIdentityTable.EMAIL;
import static org.apache.james.jmap.postgres.identity.PostgresCustomIdentityModule.PostgresCustomIdentityTable.HTML_SIGNATURE;
import static org.apache.james.jmap.postgres.identity.PostgresCustomIdentityModule.PostgresCustomIdentityTable.ID;
import static org.apache.james.jmap.postgres.identity.PostgresCustomIdentityModule.PostgresCustomIdentityTable.MAY_DELETE;
import static org.apache.james.jmap.postgres.identity.PostgresCustomIdentityModule.PostgresCustomIdentityTable.NAME;
import static org.apache.james.jmap.postgres.identity.PostgresCustomIdentityModule.PostgresCustomIdentityTable.REPLY_TO;
import static org.apache.james.jmap.postgres.identity.PostgresCustomIdentityModule.PostgresCustomIdentityTable.SORT_ORDER;
import static org.apache.james.jmap.postgres.identity.PostgresCustomIdentityModule.PostgresCustomIdentityTable.TABLE_NAME;
import static org.apache.james.jmap.postgres.identity.PostgresCustomIdentityModule.PostgresCustomIdentityTable.TEXT_SIGNATURE;
import static org.apache.james.jmap.postgres.identity.PostgresCustomIdentityModule.PostgresCustomIdentityTable.USERNAME;

import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.identity.CustomIdentityDAO;
import org.apache.james.jmap.api.identity.IdentityCreationRequest;
import org.apache.james.jmap.api.identity.IdentityNotFoundException;
import org.apache.james.jmap.api.identity.IdentityUpdate;
import org.apache.james.jmap.api.model.EmailAddress;
import org.apache.james.jmap.api.model.Identity;
import org.apache.james.jmap.api.model.IdentityId;
import org.jooq.JSON;
import org.jooq.Record;
import org.reactivestreams.Publisher;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scala.publisher.SMono;
import scala.Option;
import scala.collection.immutable.Seq;
import scala.jdk.javaapi.CollectionConverters;
import scala.jdk.javaapi.OptionConverters;
import scala.runtime.BoxedUnit;

public class PostgresCustomIdentityDAO implements CustomIdentityDAO {
    static class Email {
        private final String name;
        private final String email;

        @JsonCreator
        public Email(@JsonProperty("name") String name,
                     @JsonProperty("email") String email) {
            this.name = name;
            this.email = email;
        }

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }
    }

    private final PostgresExecutor.Factory executorFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    public PostgresCustomIdentityDAO(PostgresExecutor.Factory executorFactory) {
        this.executorFactory = executorFactory;
    }

    @Override
    public Publisher<Identity> save(Username user, IdentityCreationRequest creationRequest) {
        return save(user, IdentityId.generate(), creationRequest);
    }

    @Override
    public Publisher<Identity> save(Username user, IdentityId identityId, IdentityCreationRequest creationRequest) {
        final Identity identity = creationRequest.asIdentity(identityId);
        return upsertReturnMono(user, identity);
    }

    @Override
    public Publisher<Identity> list(Username user) {
        return executorFactory.create(user.getDomainPart())
            .executeRows(dslContext -> Flux.from(dslContext.selectFrom(TABLE_NAME)
                .where(USERNAME.eq(user.asString()))))
            .map(Throwing.function(this::readRecord));
    }

    @Override
    public SMono<Identity> findByIdentityId(Username user, IdentityId identityId) {
        return SMono.fromPublisher(executorFactory.create(user.getDomainPart())
            .executeRow(dslContext -> Mono.from(dslContext.selectFrom(TABLE_NAME)
                .where(USERNAME.eq(user.asString()))
                .and(ID.eq(identityId.id()))))
            .map(Throwing.function(this::readRecord)));
    }

    @Override
    public Publisher<BoxedUnit> update(Username user, IdentityId identityId, IdentityUpdate identityUpdate) {
        return Mono.from(findByIdentityId(user, identityId))
            .switchIfEmpty(Mono.error(new IdentityNotFoundException(identityId)))
            .map(identityUpdate::update)
            .flatMap(identity -> upsertReturnMono(user, identity))
            .thenReturn(BoxedUnit.UNIT);
    }

    @Override
    public SMono<BoxedUnit> upsert(Username user, Identity patch) {
        return SMono.fromPublisher(upsertReturnMono(user, patch)
            .thenReturn(BoxedUnit.UNIT));
    }

    private Mono<Identity> upsertReturnMono(Username user, Identity identity) {
        return executorFactory.create(user.getDomainPart())
            .executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME)
                .set(USERNAME, user.asString())
                .set(ID, identity.id().id())
                .set(NAME, identity.name())
                .set(EMAIL, identity.email().asString())
                .set(TEXT_SIGNATURE, identity.textSignature())
                .set(HTML_SIGNATURE, identity.htmlSignature())
                .set(MAY_DELETE, identity.mayDelete())
                .set(SORT_ORDER, identity.sortOrder())
                .set(REPLY_TO, convertToJooqJson(identity.replyTo()))
                .set(BCC, convertToJooqJson(identity.bcc()))
                .onConflict(USERNAME, ID)
                .doUpdate()
                .set(NAME, identity.name())
                .set(EMAIL, identity.email().asString())
                .set(TEXT_SIGNATURE, identity.textSignature())
                .set(HTML_SIGNATURE, identity.htmlSignature())
                .set(MAY_DELETE, identity.mayDelete())
                .set(SORT_ORDER, identity.sortOrder())
                .set(REPLY_TO, convertToJooqJson(identity.replyTo()))
                .set(BCC, convertToJooqJson(identity.bcc()))))
            .thenReturn(identity);
    }

    @Override
    public Publisher<BoxedUnit> delete(Username username, Seq<IdentityId> ids) {
        return executorFactory.create(username.getDomainPart())
            .executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
                .where(USERNAME.eq(username.asString()))
                .and(ID.in(CollectionConverters.asJavaCollection(ids).stream().map(IdentityId::id).collect(ImmutableList.toImmutableList())))))
            .thenReturn(BoxedUnit.UNIT);
    }

    @Override
    public Publisher<BoxedUnit> delete(Username username) {
        return executorFactory.create(username.getDomainPart())
            .executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
                .where(USERNAME.eq(username.asString()))))
            .thenReturn(BoxedUnit.UNIT);
    }

    private Identity readRecord(Record record) throws Exception {
        return new Identity(new IdentityId(record.get(ID)),
            record.get(SORT_ORDER),
            record.get(NAME),
            new MailAddress(record.get(EMAIL)),
            convertToScala(record.get(REPLY_TO)),
            convertToScala(record.get(BCC)),
            record.get(TEXT_SIGNATURE),
            record.get(HTML_SIGNATURE),
            record.get(MAY_DELETE));
    }

    private Option<scala.collection.immutable.List<EmailAddress>> convertToScala(JSON json) {
        return OptionConverters.toScala(Optional.of(CollectionConverters.asScala(convertToObject(json.data())
                .stream()
                .map(Throwing.function(email -> EmailAddress.from(Optional.ofNullable(email.getName()), new MailAddress(email.getEmail()))))
                .iterator())
            .toList()));
    }

    private JSON convertToJooqJson(Option<scala.collection.immutable.List<EmailAddress>> maybeEmailAddresses) {
        return convertToJooqJson(OptionConverters.toJava(maybeEmailAddresses).map(emailAddresses ->
                CollectionConverters.asJavaCollection(emailAddresses).stream()
                    .map(emailAddress -> new Email(emailAddress.nameAsString(),
                        emailAddress.email().asString())).collect(ImmutableList.toImmutableList()))
            .orElse(ImmutableList.of()));
    }

    private JSON convertToJooqJson(List<Email> list) {
        try {
            return JSON.json(objectMapper.writeValueAsString(list));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Email> convertToObject(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
