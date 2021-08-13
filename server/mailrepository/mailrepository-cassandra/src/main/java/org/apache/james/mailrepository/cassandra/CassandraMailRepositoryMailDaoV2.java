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

import static com.datastax.driver.core.DataType.text;
import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
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
import java.util.Optional;

import javax.inject.Inject;
import javax.mail.internet.AddressException;

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

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.TupleType;
import com.datastax.driver.core.TupleValue;
import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraMailRepositoryMailDaoV2 implements CassandraMailRepositoryMailDaoAPI {

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insertMail;
    private final PreparedStatement deleteMail;
    private final PreparedStatement selectMail;
    private final PreparedStatement litBlobs;
    private final BlobId.Factory blobIdFactory;
    private final TupleType userHeaderNameHeaderValueTriple;

    @Inject
    @VisibleForTesting
    CassandraMailRepositoryMailDaoV2(Session session, BlobId.Factory blobIdFactory) {
        this.executor = new CassandraAsyncExecutor(session);

        this.insertMail = prepareInsert(session);
        this.deleteMail = prepareDelete(session);
        this.selectMail = prepareSelect(session);
        this.litBlobs = prepareListBlobs(session);
        this.blobIdFactory = blobIdFactory;
        this.userHeaderNameHeaderValueTriple = session.getCluster().getMetadata().newTupleType(text(), text(), text());
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(delete()
            .from(CONTENT_TABLE_NAME)
            .where(eq(REPOSITORY_NAME, bindMarker(REPOSITORY_NAME)))
            .and(eq(MAIL_KEY, bindMarker(MAIL_KEY))));
    }

    private PreparedStatement prepareListBlobs(Session session) {
        return session.prepare(
            select(HEADER_BLOB_ID, BODY_BLOB_ID)
                .from(CONTENT_TABLE_NAME));
    }

    private PreparedStatement prepareInsert(Session session) {
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
            .value(PER_RECIPIENT_SPECIFIC_HEADERS, bindMarker(PER_RECIPIENT_SPECIFIC_HEADERS)));
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(
            select(MAIL_PROPERTIES)
                .from(CONTENT_TABLE_NAME)
                .where(eq(REPOSITORY_NAME, bindMarker(REPOSITORY_NAME)))
                .and(eq(MAIL_KEY, bindMarker(MAIL_KEY))));
    }

    public Mono<Void> store(MailRepositoryUrl url, Mail mail, BlobId headerId, BlobId bodyId) {
        return Mono.fromCallable(() -> {
            BoundStatement boundStatement = insertMail.bind()
                .setString(REPOSITORY_NAME, url.asString())
                .setString(MAIL_KEY, mail.getName())
                .setString(HEADER_BLOB_ID, headerId.asString())
                .setString(BODY_BLOB_ID, bodyId.asString())
                .setString(STATE, mail.getState())
                .setList(RECIPIENTS, asStringList(mail.getRecipients()))
                .setString(REMOTE_ADDR, mail.getRemoteAddr())
                .setString(REMOTE_HOST, mail.getRemoteHost())
                .setTimestamp(LAST_UPDATED, mail.getLastUpdated())
                .setMap(ATTRIBUTES, toRawAttributeMap(mail))
                .setList(PER_RECIPIENT_SPECIFIC_HEADERS, toTupleList(mail.getPerRecipientSpecificHeaders()));

            Optional.ofNullable(mail.getErrorMessage())
                .ifPresent(errorMessage -> boundStatement.setString(MailRepositoryTable.ERROR_MESSAGE, mail.getErrorMessage()));

            mail.getMaybeSender()
                .asOptional()
                .map(MailAddress::asString)
                .ifPresent(mailAddress -> boundStatement.setString(MailRepositoryTable.SENDER, mailAddress));

            return boundStatement;
        })
            .flatMap(executor::executeVoid);
    }

    @Override
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
        Date lastUpdated = row.getTimestamp(LAST_UPDATED);
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
            .map(attribute -> Pair.of(attribute.getName().asString(), toJson(attribute.getValue())))
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

    private String toJson(AttributeValue<?> attributeValue) {
        return attributeValue.toJson().toString();
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
            .flatMapIterable(row -> ImmutableList.of(
                blobIdFactory.from(row.getString(HEADER_BLOB_ID)),
                blobIdFactory.from(row.getString(BODY_BLOB_ID))
            ));
    }

}
