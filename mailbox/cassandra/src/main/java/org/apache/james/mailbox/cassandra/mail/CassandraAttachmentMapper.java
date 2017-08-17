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

package org.apache.james.mailbox.cassandra.mail;

import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentTable.FIELDS;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentTable.ID;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentTable.PAYLOAD;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentTable.SIZE;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentTable.TYPE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.util.FluentFutureStream;
import org.apache.james.util.OptionalConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.ThrownByLambdaException;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;

public class CassandraAttachmentMapper implements AttachmentMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraAttachmentMapper.class);
    private final CassandraAsyncExecutor cassandraAsyncExecutor;

    @Inject
    public CassandraAttachmentMapper(Session session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
    }

    @Override
    public void endRequest() {
    }

    @Override
    public <T> T execute(Transaction<T> transaction) throws MailboxException {
        return transaction.run();
    }

    @Override
    public Attachment getAttachment(AttachmentId attachmentId) throws AttachmentNotFoundException {
        Preconditions.checkArgument(attachmentId != null);
        return cassandraAsyncExecutor.executeSingleRow(
            select(FIELDS)
                .from(TABLE_NAME)
                .where(eq(ID, attachmentId.getId())))
            .thenApply(optional -> optional.map(this::attachment))
            .join()
            .orElseThrow(() -> new AttachmentNotFoundException(attachmentId.getId()));
    }

    private Attachment attachment(Row row) {
        return Attachment.builder()
                .attachmentId(AttachmentId.from(row.getString(ID)))
                .bytes(row.getBytes(PAYLOAD).array())
                .type(row.getString(TYPE))
                .build();
    }

    @Override
    public List<Attachment> getAttachments(Collection<AttachmentId> attachmentIds) {
        return getAttachmentsAsFuture(attachmentIds).join();
    }

    public CompletableFuture<List<Attachment>> getAttachmentsAsFuture(Collection<AttachmentId> attachmentIds) {
        Preconditions.checkArgument(attachmentIds != null);

        Stream<CompletableFuture<Optional<Attachment>>> attachments = attachmentIds
                .stream()
                .distinct()
                .map(this::getAttachmentAsFuture);

        return FluentFutureStream
            .of(attachments)
            .flatMap(OptionalConverter::toStream)
            .completableFuture()
            .thenApply(stream ->
                stream.collect(Guavate.toImmutableList()));
    }

    private CompletableFuture<Optional<Attachment>> getAttachmentAsFuture(AttachmentId attachmentId) {
        String id = attachmentId.getId();

        return cassandraAsyncExecutor.executeSingleRow(
            select(FIELDS)
                .from(TABLE_NAME)
                .where(eq(ID, id)))
            .thenApply(optional ->
                OptionalConverter.ifEmpty(
                    optional.map(this::attachment),
                    () -> LOGGER.warn("Failed retrieving attachment {}", attachmentId)));
    }

    @Override
    public void storeAttachment(Attachment attachment) throws MailboxException {
        try {
            asyncStoreAttachment(attachment).join();
        } catch (IOException e) {
            throw new MailboxException(e.getMessage(), e);
        }
    }

    private CompletableFuture<Void> asyncStoreAttachment(Attachment attachment) throws IOException {
        return cassandraAsyncExecutor.executeVoid(
            insertInto(TABLE_NAME)
                .value(ID, attachment.getAttachmentId().getId())
                .value(PAYLOAD, ByteBuffer.wrap(attachment.getBytes()))
                .value(TYPE, attachment.getType())
                .value(SIZE, attachment.getSize())
        );
    }

    @Override
    public void storeAttachments(Collection<Attachment> attachments) throws MailboxException {
        try {
            FluentFutureStream.of(
                attachments.stream()
                    .map(Throwing.function(this::asyncStoreAttachment)))
                .join();
        } catch (ThrownByLambdaException e) {
            throw new MailboxException(e.getCause().getMessage(), e.getCause());
        }
    }
}
