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

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.ATTRIBUTES;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.BODY_BLOB_ID;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.CONTENT_TABLE_NAME;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.ERROR_MESSAGE;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.HEADER_BLOB_ID;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.HEADER_NAME;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.HEADER_TYPE;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.HEADER_VALUE;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.LAST_UPDATED;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.MAIL_KEY;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.MAIL_PROPERTIES;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.MESSAGE_SIZE;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.PER_RECIPIENT_SPECIFIC_HEADERS;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.RECIPIENTS;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.REMOTE_ADDR;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.REMOTE_HOST;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.REPOSITORY_NAME;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.SENDER;
import static org.apache.james.mailrepository.cassandra.MailRepositoryTable.STATE;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.mail.internet.AddressException;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
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

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.UDTValue;
import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;

public class CassandraMailRepositoryMailDAO implements CassandraMailRepositoryMailDaoAPI {

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insertMail;
    private final PreparedStatement deleteMail;
    private final PreparedStatement selectMail;
    private final BlobId.Factory blobIdFactory;
    private final CassandraTypesProvider cassandraTypesProvider;

    @Inject
    @VisibleForTesting
    CassandraMailRepositoryMailDAO(Session session, BlobId.Factory blobIdFactory,
                                   CassandraTypesProvider cassandraTypesProvider) {
        this.executor = new CassandraAsyncExecutor(session);

        this.insertMail = prepareInsert(session);
        this.deleteMail = prepareDelete(session);
        this.selectMail = prepareSelect(session);
        this.blobIdFactory = blobIdFactory;
        this.cassandraTypesProvider = cassandraTypesProvider;
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(delete()
            .from(CONTENT_TABLE_NAME)
            .where(eq(REPOSITORY_NAME, bindMarker(REPOSITORY_NAME)))
            .and(eq(MAIL_KEY, bindMarker(MAIL_KEY))));
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(insertInto(CONTENT_TABLE_NAME)
            .value(REPOSITORY_NAME, bindMarker(REPOSITORY_NAME))
            .value(MAIL_KEY, bindMarker(MAIL_KEY))
            .value(MESSAGE_SIZE, bindMarker(MESSAGE_SIZE))
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

    @Override
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
                .setLong(MESSAGE_SIZE, mail.getMessageSize())
                .setTimestamp(LAST_UPDATED, mail.getLastUpdated())
                .setMap(ATTRIBUTES, toRawAttributeMap(mail))
                .setMap(PER_RECIPIENT_SPECIFIC_HEADERS, toHeaderMap(mail.getPerRecipientSpecificHeaders()));

            Optional.ofNullable(mail.getErrorMessage())
                .ifPresent(errorMessage -> boundStatement.setString(ERROR_MESSAGE, mail.getErrorMessage()));

            mail.getMaybeSender()
                .asOptional()
                .map(MailAddress::asString)
                .ifPresent(mailAddress -> boundStatement.setString(SENDER, mailAddress));
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

    @Override
    public Mono<Optional<MailDTO>> read(MailRepositoryUrl url, MailKey key) {
        return executor.executeSingleRowOptional(selectMail.bind()
                .setString(REPOSITORY_NAME, url.asString())
                .setString(MAIL_KEY, key.asString()))
            .map(rowOptional -> rowOptional.map(this::toMail));
    }

    private MailDTO toMail(Row row) {
        MaybeSender sender = Optional.ofNullable(row.getString(SENDER))
            .map(MaybeSender::getMailSender)
            .orElse(MaybeSender.nullSender());
        List<MailAddress> recipients = row.getList(RECIPIENTS, String.class)
            .stream()
            .map(Throwing.function(MailAddress::new))
            .collect(Guavate.toImmutableList());
        String state = row.getString(STATE);
        String remoteAddr = row.getString(REMOTE_ADDR);
        String remoteHost = row.getString(REMOTE_HOST);
        String errorMessage = row.getString(ERROR_MESSAGE);
        String name = row.getString(MAIL_KEY);
        Date lastUpdated = row.getTimestamp(LAST_UPDATED);
        Map<String, ByteBuffer> rawAttributes = row.getMap(ATTRIBUTES, String.class, ByteBuffer.class);
        PerRecipientHeaders perRecipientHeaders = fromHeaderMap(row.getMap(PER_RECIPIENT_SPECIFIC_HEADERS, String.class, UDTValue.class));

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

    private ImmutableList<Attribute> toAttributes(Map<String, ByteBuffer> rowAttributes) {
        return rowAttributes.entrySet()
            .stream()
            .map(entry -> new Attribute(AttributeName.of(entry.getKey()), fromByteBuffer(entry.getValue())))
            .collect(Guavate.toImmutableList());
    }

    private ImmutableList<String> asStringList(Collection<MailAddress> mailAddresses) {
        return mailAddresses.stream().map(MailAddress::asString).collect(Guavate.toImmutableList());
    }

    private ImmutableMap<String, ByteBuffer> toRawAttributeMap(Mail mail) {
        return mail.attributes()
            .map(attribute -> Pair.of(attribute.getName(), attribute.getValue()))
            .collect(Guavate.toImmutableMap(
                pair -> pair.getLeft().asString(),
                pair -> toByteBuffer((Serializable) pair.getRight().value())));
    }

    private ImmutableMap<String, UDTValue> toHeaderMap(PerRecipientHeaders perRecipientHeaders) {
        return perRecipientHeaders.getHeadersByRecipient()
            .asMap()
            .entrySet()
            .stream()
            .flatMap(entry -> entry.getValue().stream().map(value -> Pair.of(entry.getKey(), value)))
            .map(entry -> Pair.of(entry.getKey().asString(),
                cassandraTypesProvider.getDefinedUserType(HEADER_TYPE)
                    .newValue()
                    .setString(HEADER_NAME, entry.getRight().getName())
                    .setString(HEADER_VALUE, entry.getRight().getValue())))
            .collect(Guavate.toImmutableMap(Pair::getLeft, Pair::getRight));
    }

    private PerRecipientHeaders fromHeaderMap(Map<String, UDTValue> rawMap) {
        PerRecipientHeaders result = new PerRecipientHeaders();

        rawMap.forEach((key, value) -> result.addHeaderForRecipient(PerRecipientHeaders.Header.builder()
                .name(value.getString(HEADER_NAME))
                .value(value.getString(HEADER_VALUE))
                .build(),
            toMailAddress(key)));
        return result;
    }

    private ByteBuffer toByteBuffer(Serializable serializable) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            new ObjectOutputStream(outputStream).writeObject(serializable);
            return ByteBuffer.wrap(outputStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private AttributeValue<?> fromByteBuffer(ByteBuffer byteBuffer) {
        try {
            byte[] data = new byte[byteBuffer.remaining()];
            byteBuffer.get(data);
            ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(data));
            return AttributeValue.ofAny(objectInputStream.readObject());
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private MailAddress toMailAddress(String rawValue) {
        try {
            return new MailAddress(rawValue);
        } catch (AddressException e) {
            throw new RuntimeException(e);
        }
    }

}
