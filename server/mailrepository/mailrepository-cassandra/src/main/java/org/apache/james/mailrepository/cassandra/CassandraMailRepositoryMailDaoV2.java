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

package org.apache.james.mailrepository.cassandra;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static java.util.List.of;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTableV2.ATTRIBUTES;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTableV2.BODY_BLOB_ID;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTableV2.CONTENT_TABLE_NAME;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTableV2.ERROR_MESSAGE;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTableV2.HEADER_BLOB_ID;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTableV2.HeaderEntry.HEADER_NAME_INDEX;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTableV2.HeaderEntry.HEADER_VALUE_INDEX;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTableV2.HeaderEntry.USER_INDEX;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTableV2.LAST_UPDATED;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTableV2.MAIL_KEY;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTableV2.MAIL_PROPERTIES;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTableV2.PER_RECIPIENT_SPECIFIC_HEADERS;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTableV2.RECIPIENTS;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTableV2.REMOTE_ADDR;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTableV2.REMOTE_HOST;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTableV2.REPOSITORY_NAME;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTableV2.SENDER;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTableV2.STATE;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.inject.Inject;

import jakarta.mail.internet.AddressException;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.blob.api.BlobId;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.PerRecipientHeaders.Header;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.data.TupleValue;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.TupleType;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraMailRepositoryMailDaoV2 {

    public  static class MailDTO {
        private final MailImpl.Builder mailBuilder;
        private final BlobId headerBlobId;
        private final BlobId bodyBlobId;

        public MailDTO(MailImpl.Builder mailBuilder, BlobId headerBlobId, BlobId bodyBlobId) {
            this.mailBuilder = mailBuilder;
            this.headerBlobId = headerBlobId;
            this.bodyBlobId = bodyBlobId;
        }

        public MailImpl.Builder getMailBuilder() {
            return mailBuilder;
        }

        public BlobId getHeaderBlobId() {
            return headerBlobId;
        }

        public BlobId getBodyBlobId() {
            return bodyBlobId;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof MailDTO) {
                MailDTO mailDTO = (MailDTO) o;

                return Objects.equals(this.mailBuilder.build(), mailDTO.mailBuilder.build())
                    && Objects.equals(this.headerBlobId, mailDTO.headerBlobId)
                    && Objects.equals(this.bodyBlobId, mailDTO.bodyBlobId);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(mailBuilder.build(), headerBlobId, bodyBlobId);
        }
    }

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insertMail;
    private final PreparedStatement deleteMail;
    private final PreparedStatement selectMail;
    private final PreparedStatement litBlobs;
    private final BlobId.Factory blobIdFactory;
    private final TupleType userHeaderNameHeaderValueTriple;

    @Inject
    @VisibleForTesting
    CassandraMailRepositoryMailDaoV2(CqlSession session, BlobId.Factory blobIdFactory) {
        this.executor = new CassandraAsyncExecutor(session);

        this.insertMail = prepareInsert(session);
        this.deleteMail = prepareDelete(session);
        this.selectMail = prepareSelect(session);
        this.litBlobs = prepareListBlobs(session);
        this.blobIdFactory = blobIdFactory;
        this.userHeaderNameHeaderValueTriple = DataTypes.tupleOf(DataTypes.TEXT, DataTypes.TEXT, DataTypes.TEXT);
    }

    private PreparedStatement prepareDelete(CqlSession session) {
        return session.prepare(deleteFrom(CONTENT_TABLE_NAME)
            .where(of(column(REPOSITORY_NAME).isEqualTo(bindMarker(REPOSITORY_NAME)),
                column(MAIL_KEY).isEqualTo(bindMarker(MAIL_KEY))))
            .build());
    }

    private PreparedStatement prepareListBlobs(CqlSession session) {
        return session.prepare(
            selectFrom(CONTENT_TABLE_NAME)
                .columns(HEADER_BLOB_ID, BODY_BLOB_ID).build());
    }

    private PreparedStatement prepareInsert(CqlSession session) {
        return session.prepare(insertInto(CONTENT_TABLE_NAME)
            .value(REPOSITORY_NAME, bindMarker(REPOSITORY_NAME))
            .value(MAIL_KEY, bindMarker(MAIL_KEY))
            .value(STATE, bindMarker(STATE))
            .value(SENDER, bindMarker(SENDER))
            .value(RECIPIENTS, bindMarker(RECIPIENTS))
            .value(ATTRIBUTES, bindMarker(ATTRIBUTES))
            .value(ERROR_MESSAGE, bindMarker(ERROR_MESSAGE))
            .value(REMOTE_ADDR, bindMarker(REMOTE_ADDR))
            .value(REMOTE_HOST, bindMarker(REMOTE_HOST))
            .value(LAST_UPDATED, bindMarker(LAST_UPDATED))
            .value(HEADER_BLOB_ID, bindMarker(HEADER_BLOB_ID))
            .value(BODY_BLOB_ID, bindMarker(BODY_BLOB_ID))
            .value(PER_RECIPIENT_SPECIFIC_HEADERS, bindMarker(PER_RECIPIENT_SPECIFIC_HEADERS))
            .build());
    }

    private PreparedStatement prepareSelect(CqlSession session) {
        return session.prepare(
            selectFrom(CONTENT_TABLE_NAME)
                .columns(MAIL_PROPERTIES)
                .where(of(column(REPOSITORY_NAME).isEqualTo(bindMarker(REPOSITORY_NAME)),
                    column(MAIL_KEY).isEqualTo(bindMarker(MAIL_KEY))))
                .build());
    }

    public Mono<Void> store(MailRepositoryUrl url, Mail mail, BlobId headerId, BlobId bodyId) {
        return Mono.fromCallable(() -> {
                BoundStatementBuilder statement = insertMail.boundStatementBuilder()
                    .setString(REPOSITORY_NAME, url.asString())
                    .setString(MAIL_KEY, mail.getName())
                    .setString(HEADER_BLOB_ID, headerId.asString())
                    .setString(BODY_BLOB_ID, bodyId.asString())
                    .setString(STATE, mail.getState())
                    .setList(RECIPIENTS, asStringList(mail.getRecipients()), String.class)
                    .setString(REMOTE_ADDR, mail.getRemoteAddr())
                    .setString(REMOTE_HOST, mail.getRemoteHost())
                    .setInstant(LAST_UPDATED, Optional.ofNullable(mail.getLastUpdated()).map(Date::toInstant).orElse(null))
                    .setMap(ATTRIBUTES, toRawAttributeMap(mail), String.class, String.class)
                    .setList(PER_RECIPIENT_SPECIFIC_HEADERS, toTupleList(mail.getPerRecipientSpecificHeaders()), TupleValue.class);

                Optional.ofNullable(mail.getErrorMessage())
                    .ifPresent(errorMessage -> statement.setString(MailRepositoryTable.ERROR_MESSAGE, mail.getErrorMessage()));

                mail.getMaybeSender()
                    .asOptional()
                    .map(MailAddress::asString)
                    .ifPresent(mailAddress -> statement.setString(MailRepositoryTable.SENDER, mailAddress));

                return statement.build();
            })
            .flatMap(executor::executeVoid);
    }

    public Mono<Void> remove(MailRepositoryUrl url, MailKey key) {
        return executor.executeVoid(deleteMail.bind()
            .setString(REPOSITORY_NAME, url.asString())
            .setString(MAIL_KEY, key.asString()));
    }

    public Mono<Optional<MailDTO>> read(MailRepositoryUrl url, MailKey key) {
        return executor.executeSingleRowOptional(selectMail.bind()
                .setString(REPOSITORY_NAME, url.asString())
                .setString(MAIL_KEY, key.asString()))
            .map(rowOptional -> rowOptional.map(this::toMail));
    }

    private MailDTO toMail(Row row) {
        MaybeSender sender = MaybeSender.getMailSender(row.getString(SENDER));
        List<MailAddress> recipients = row.getList(RECIPIENTS, String.class)
            .stream()
            .map(Throwing.function(MailAddress::new))
            .collect(ImmutableList.toImmutableList());
        String state = row.getString(STATE);
        String remoteAddr = row.getString(REMOTE_ADDR);
        String remoteHost = row.getString(REMOTE_HOST);
        String errorMessage = row.getString(ERROR_MESSAGE);
        String name = row.getString(MAIL_KEY);
        Date lastUpdated = Optional.ofNullable(row.getInstant(LAST_UPDATED))
            .map(Date::from)
            .orElse(null);

        Map<String, String> rawAttributes = row.getMap(ATTRIBUTES, String.class, String.class);
        PerRecipientHeaders perRecipientHeaders = fromList(row.getList(PER_RECIPIENT_SPECIFIC_HEADERS, TupleValue.class));

        MailImpl.Builder mailBuilder = MailImpl.builder()
            .name(name)
            .sender(sender)
            .addRecipients(recipients)
            .lastUpdated(lastUpdated)
            .errorMessage(errorMessage)
            .remoteHost(remoteHost)
            .remoteAddr(remoteAddr)
            .state(state)
            .addAllHeadersForRecipients(perRecipientHeaders)
            .addAttributes(toAttributes(rawAttributes));

        return new MailDTO(mailBuilder,
            blobIdFactory.from(row.getString(HEADER_BLOB_ID)),
            blobIdFactory.from(row.getString(BODY_BLOB_ID)));
    }

    private List<Attribute> toAttributes(Map<String, String> rowAttributes) {
        return rowAttributes.entrySet()
            .stream()
            .map(Throwing.function(entry -> new Attribute(AttributeName.of(entry.getKey()), AttributeValue.fromJsonString(entry.getValue()))))
            .collect(ImmutableList.toImmutableList());
    }

    private ImmutableList<String> asStringList(Collection<MailAddress> mailAddresses) {
        return mailAddresses.stream().map(MailAddress::asString).collect(ImmutableList.toImmutableList());
    }

    private ImmutableMap<String, String> toRawAttributeMap(Mail mail) {
        return mail.attributes()
            .flatMap(attribute -> attribute.getValue().toJson().map(JsonNode::toString).map(value -> Pair.of(attribute.getName().asString(), value)).stream())
            .collect(ImmutableMap.toImmutableMap(Pair::getLeft, Pair::getRight));
    }

    private ImmutableList<TupleValue> toTupleList(PerRecipientHeaders perRecipientHeaders) {
        return perRecipientHeaders.getHeadersByRecipient()
            .entries()
            .stream()
            .map(entry -> userHeaderNameHeaderValueTriple.newValue(entry.getKey().asString(), entry.getValue().getName(), entry.getValue().getValue()))
            .collect(ImmutableList.toImmutableList());
    }

    private PerRecipientHeaders fromList(List<TupleValue> list) {
        PerRecipientHeaders result = new PerRecipientHeaders();

        list.forEach(tuple ->
                result.addHeaderForRecipient(
                    Header.builder()
                        .name(tuple.getString(HEADER_NAME_INDEX))
                        .value(tuple.getString(HEADER_VALUE_INDEX))
                        .build(),
                    toMailAddress(tuple.getString(USER_INDEX))));
        return result;
    }

    private MailAddress toMailAddress(String rawValue) {
        try {
            return new MailAddress(rawValue);
        } catch (AddressException e) {
            throw new RuntimeException(e);
        }
    }

    Flux<BlobId> listBlobs() {
        return executor.executeRows(litBlobs.bind())
            .flatMapIterable(row -> List.of(
                blobIdFactory.from(row.getString(HEADER_BLOB_ID)),
                blobIdFactory.from(row.getString(BODY_BLOB_ID))
            ));
    }

}
