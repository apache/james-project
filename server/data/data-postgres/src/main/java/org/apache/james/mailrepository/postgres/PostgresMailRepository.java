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

package org.apache.james.mailrepository.postgres;

import static org.apache.james.backends.postgres.PostgresCommons.DATE_TO_LOCAL_DATE_TIME;
import static org.apache.james.backends.postgres.PostgresCommons.LOCAL_DATE_TIME_DATE_FUNCTION;
import static org.apache.james.mailrepository.postgres.PostgresMailRepositoryModule.PostgresMailRepositoryContentTable.ATTRIBUTES;
import static org.apache.james.mailrepository.postgres.PostgresMailRepositoryModule.PostgresMailRepositoryContentTable.BODY_BLOB_ID;
import static org.apache.james.mailrepository.postgres.PostgresMailRepositoryModule.PostgresMailRepositoryContentTable.ERROR;
import static org.apache.james.mailrepository.postgres.PostgresMailRepositoryModule.PostgresMailRepositoryContentTable.HEADER_BLOB_ID;
import static org.apache.james.mailrepository.postgres.PostgresMailRepositoryModule.PostgresMailRepositoryContentTable.KEY;
import static org.apache.james.mailrepository.postgres.PostgresMailRepositoryModule.PostgresMailRepositoryContentTable.LAST_UPDATED;
import static org.apache.james.mailrepository.postgres.PostgresMailRepositoryModule.PostgresMailRepositoryContentTable.PER_RECIPIENT_SPECIFIC_HEADERS;
import static org.apache.james.mailrepository.postgres.PostgresMailRepositoryModule.PostgresMailRepositoryContentTable.RECIPIENTS;
import static org.apache.james.mailrepository.postgres.PostgresMailRepositoryModule.PostgresMailRepositoryContentTable.REMOTE_ADDRESS;
import static org.apache.james.mailrepository.postgres.PostgresMailRepositoryModule.PostgresMailRepositoryContentTable.REMOTE_HOST;
import static org.apache.james.mailrepository.postgres.PostgresMailRepositoryModule.PostgresMailRepositoryContentTable.SENDER;
import static org.apache.james.mailrepository.postgres.PostgresMailRepositoryModule.PostgresMailRepositoryContentTable.STATE;
import static org.apache.james.mailrepository.postgres.PostgresMailRepositoryModule.PostgresMailRepositoryContentTable.TABLE_NAME;
import static org.apache.james.mailrepository.postgres.PostgresMailRepositoryModule.PostgresMailRepositoryContentTable.URL;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.backends.postgres.utils.PostgresUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.server.core.MailImpl;
import org.apache.james.server.core.MimeMessageWrapper;
import org.apache.james.util.AuditTrail;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;
import org.jooq.Record;
import org.jooq.postgres.extensions.types.Hstore;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.lambdas.Throwing;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresMailRepository implements MailRepository {
    private static final String HEADERS_SEPARATOR = ";  ";

    private final PostgresExecutor postgresExecutor;
    private final MailRepositoryUrl url;
    private final Store<MimeMessage, MimeMessagePartsId> mimeMessageStore;
    private final BlobId.Factory blobIdFactory;

    @Inject
    public PostgresMailRepository(PostgresExecutor postgresExecutor,
                                  MailRepositoryUrl url,
                                  MimeMessageStore.Factory mimeMessageStoreFactory,
                                  BlobId.Factory blobIdFactory) {
        this.postgresExecutor = postgresExecutor;
        this.url = url;
        this.mimeMessageStore = mimeMessageStoreFactory.mimeMessageStore();
        this.blobIdFactory = blobIdFactory;
    }

    @Override
    public long size() throws MessagingException {
        return sizeReactive().block();
    }

    @Override
    public Mono<Long> sizeReactive() {
        return postgresExecutor.executeCount(context -> Mono.from(context.selectCount()
                .from(TABLE_NAME)
                .where(URL.eq(url.asString()))))
            .map(Integer::longValue);
    }

    @Override
    public MailKey store(Mail mail) throws MessagingException {
        MailKey mailKey = MailKey.forMail(mail);

        return storeMailBlob(mail)
            .flatMap(mimeMessagePartsId -> storeMailMetadata(mail, mailKey, mimeMessagePartsId)
                .doOnSuccess(auditTrailStoredMail(mail))
                .onErrorResume(PostgresUtils.UNIQUE_CONSTRAINT_VIOLATION_PREDICATE, e -> Mono.from(mimeMessageStore.delete(mimeMessagePartsId))
                    .thenReturn(mailKey)))
            .block();
    }

    private Mono<MimeMessagePartsId> storeMailBlob(Mail mail) throws MessagingException {
        return mimeMessageStore.save(mail.getMessage());
    }

    private Mono<MailKey> storeMailMetadata(Mail mail, MailKey mailKey, MimeMessagePartsId mimeMessagePartsId) {
        return postgresExecutor.executeVoid(context -> Mono.from(context.insertInto(TABLE_NAME)
                .set(URL, url.asString())
                .set(KEY, mailKey.asString())
                .set(HEADER_BLOB_ID, mimeMessagePartsId.getHeaderBlobId().asString())
                .set(BODY_BLOB_ID, mimeMessagePartsId.getBodyBlobId().asString())
                .set(STATE, mail.getState())
                .set(ERROR, mail.getErrorMessage())
                .set(SENDER, mail.getMaybeSender().asString())
                .set(RECIPIENTS, asStringArray(mail.getRecipients()))
                .set(REMOTE_ADDRESS, mail.getRemoteAddr())
                .set(REMOTE_HOST, mail.getRemoteHost())
                .set(LAST_UPDATED, DATE_TO_LOCAL_DATE_TIME.apply(mail.getLastUpdated()))
                .set(ATTRIBUTES, asHstore(mail.attributes()))
                .set(PER_RECIPIENT_SPECIFIC_HEADERS, asHstore(mail.getPerRecipientSpecificHeaders().getHeadersByRecipient()))
                .onConflict(URL, KEY)
                .doUpdate()
                .set(HEADER_BLOB_ID, mimeMessagePartsId.getHeaderBlobId().asString())
                .set(BODY_BLOB_ID, mimeMessagePartsId.getBodyBlobId().asString())
                .set(STATE, mail.getState())
                .set(ERROR, mail.getErrorMessage())
                .set(SENDER, mail.getMaybeSender().asString())
                .set(RECIPIENTS, asStringArray(mail.getRecipients()))
                .set(REMOTE_ADDRESS, mail.getRemoteAddr())
                .set(REMOTE_HOST, mail.getRemoteHost())
                .set(LAST_UPDATED, DATE_TO_LOCAL_DATE_TIME.apply(mail.getLastUpdated()))
                .set(ATTRIBUTES, asHstore(mail.attributes()))
                .set(PER_RECIPIENT_SPECIFIC_HEADERS, asHstore(mail.getPerRecipientSpecificHeaders().getHeadersByRecipient()))
            ))
            .thenReturn(mailKey);
    }

    private Consumer<MailKey> auditTrailStoredMail(Mail mail) {
        return Throwing.consumer(any -> AuditTrail.entry()
            .protocol("mailrepository")
            .action("store")
            .parameters(Throwing.supplier(() -> ImmutableMap.of("mailId", mail.getName(),
                "mimeMessageId", Optional.ofNullable(mail.getMessage())
                    .map(Throwing.function(MimeMessage::getMessageID))
                    .orElse(""),
                "sender", mail.getMaybeSender().asString(),
                "recipients", StringUtils.join(mail.getRecipients()))))
            .log("PostgresMailRepository stored mail."));
    }

    private String[] asStringArray(Collection<MailAddress> mailAddresses) {
        return mailAddresses.stream()
            .map(MailAddress::asString)
            .toArray(String[]::new);
    }

    private Hstore asHstore(Multimap<MailAddress, PerRecipientHeaders.Header> multimap) {
        return Hstore.hstore(multimap
            .asMap()
            .entrySet()
            .stream()
            .map(recipientToHeaders -> Pair.of(recipientToHeaders.getKey().asString(),
                asString(recipientToHeaders.getValue())))
            .collect(ImmutableMap.toImmutableMap(Pair::getLeft, Pair::getRight)));
    }

    private String asString(Collection<PerRecipientHeaders.Header> headers) {
        return StringUtils.join(headers.stream()
            .map(PerRecipientHeaders.Header::asString)
            .collect(ImmutableList.toImmutableList()), HEADERS_SEPARATOR);
    }

    private Hstore asHstore(Stream<Attribute> attributes) {
        return Hstore.hstore(attributes
            .flatMap(attribute -> attribute.getValue()
                .toJson()
                .map(JsonNode::toString)
                .map(value -> Pair.of(attribute.getName().asString(), value)).stream())
            .collect(ImmutableMap.toImmutableMap(Pair::getLeft, Pair::getRight)));
    }

    @Override
    public Iterator<MailKey> list() throws MessagingException {
        return listMailKeys()
            .toStream()
            .iterator();
    }

    private Flux<MailKey> listMailKeys() {
        return postgresExecutor.executeRows(context -> Flux.from(context.select(KEY)
                .from(TABLE_NAME)
                .where(URL.eq(url.asString()))))
            .map(record -> new MailKey(record.get(KEY)));
    }

    @Override
    public Mail retrieve(MailKey key) {
        return postgresExecutor.executeRow(context -> Mono.from(context.select()
                .from(TABLE_NAME)
                .where(URL.eq(url.asString()))
                .and(KEY.eq(key.asString()))))
            .flatMap(this::toMail)
            .blockOptional()
            .orElse(null);
    }

    private Mono<Mail> toMail(Record record) {
        return mimeMessageStore.read(toMimeMessagePartsId(record))
            .map(Throwing.function(mimeMessage -> toMail(record, mimeMessage)));
    }

    private Mail toMail(Record record, MimeMessage mimeMessage) throws MessagingException {
        List<MailAddress> recipients = Arrays.stream(record.get(RECIPIENTS))
            .map(Throwing.function(MailAddress::new))
            .collect(ImmutableList.toImmutableList());

        PerRecipientHeaders perRecipientHeaders = getPerRecipientHeaders(record);

        List<Attribute> attributes = Hstore.hstore(record.get(ATTRIBUTES, LinkedHashMap.class))
            .data()
            .entrySet()
            .stream()
            .map(Throwing.function(entry -> new Attribute(AttributeName.of(entry.getKey()),
                AttributeValue.fromJsonString(entry.getValue()))))
            .collect(ImmutableList.toImmutableList());

        MailImpl mail = MailImpl.builder()
            .name(record.get(KEY))
            .sender(MaybeSender.getMailSender(record.get(SENDER)))
            .addRecipients(recipients)
            .lastUpdated(LOCAL_DATE_TIME_DATE_FUNCTION.apply(record.get(LAST_UPDATED, LocalDateTime.class)))
            .errorMessage(record.get(ERROR))
            .remoteHost(record.get(REMOTE_HOST))
            .remoteAddr(record.get(REMOTE_ADDRESS))
            .state(record.get(STATE))
            .addAllHeadersForRecipients(perRecipientHeaders)
            .addAttributes(attributes)
            .build();

        if (mimeMessage instanceof MimeMessageWrapper) {
            mail.setMessageNoCopy((MimeMessageWrapper) mimeMessage);
        } else {
            mail.setMessage(mimeMessage);
        }

        return mail;
    }

    private PerRecipientHeaders getPerRecipientHeaders(Record record) {
        PerRecipientHeaders perRecipientHeaders = new PerRecipientHeaders();

        Hstore.hstore(record.get(PER_RECIPIENT_SPECIFIC_HEADERS, LinkedHashMap.class))
            .data()
            .entrySet()
            .stream()
            .flatMap(this::recipientToHeaderStream)
            .forEach(recipientToHeaderPair -> perRecipientHeaders.addHeaderForRecipient(
                recipientToHeaderPair.getRight(),
                recipientToHeaderPair.getLeft()));

        return perRecipientHeaders;
    }

    private Stream<Pair<MailAddress, PerRecipientHeaders.Header>> recipientToHeaderStream(Map.Entry<String, String> recipientToHeadersString) {
        List<String> headers = Splitter.on(HEADERS_SEPARATOR)
            .splitToList(recipientToHeadersString.getValue());

        return headers
            .stream()
            .map(headerAsString -> Pair.of(
                    asMailAddress(recipientToHeadersString.getKey()),
                PerRecipientHeaders.Header.fromString(headerAsString)));
    }

    private MailAddress asMailAddress(String mailAddress) {
        return Throwing.supplier(() -> new MailAddress(mailAddress))
            .get();
    }

    private MimeMessagePartsId toMimeMessagePartsId(Record record) {
        return MimeMessagePartsId.builder()
            .headerBlobId(blobIdFactory.from(record.get(HEADER_BLOB_ID)))
            .bodyBlobId(blobIdFactory.from(record.get(BODY_BLOB_ID)))
            .build();
    }

    @Override
    public void remove(MailKey key) {
        removeReactive(key).block();
    }

    private Mono<Void> removeReactive(MailKey key) {
        return getMimeMessagePartsId(key)
            .flatMap(mimeMessagePartsId -> deleteMailMetadata(key)
                .then(deleteMailBlob(mimeMessagePartsId)));
    }

    private Mono<MimeMessagePartsId> getMimeMessagePartsId(MailKey key) {
        return postgresExecutor.executeRow(context -> Mono.from(context.select(HEADER_BLOB_ID, BODY_BLOB_ID)
                .from(TABLE_NAME)
                .where(URL.eq(url.asString()))
                .and(KEY.eq(key.asString()))))
            .map(this::toMimeMessagePartsId);
    }

    private Mono<Void> deleteMailMetadata(MailKey key) {
        return postgresExecutor.executeVoid(context -> Mono.from(context.deleteFrom(TABLE_NAME)
            .where(URL.eq(url.asString()))
            .and(KEY.eq(key.asString()))));
    }

    private Mono<Void> deleteMailBlob(MimeMessagePartsId mimeMessagePartsId) {
        return Mono.from(mimeMessageStore.delete(mimeMessagePartsId));
    }

    @Override
    public void remove(Collection<MailKey> keys) {
        Flux.fromIterable(keys)
            .concatMap(this::removeReactive)
            .then()
            .block();
    }

    @Override
    public void removeAll() {
        listMailKeys()
            .flatMap(this::removeReactive, DEFAULT_CONCURRENCY)
            .then()
            .block();
    }
}
