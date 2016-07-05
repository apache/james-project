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
import static com.datastax.driver.core.querybuilder.QueryBuilder.in;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentTable.FIELDS;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentTable.ID;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentTable.NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentTable.PAYLOAD;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentTable.SIZE;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentTable.TYPE;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.io.IOUtils;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.model.Attachment;
import org.apache.james.mailbox.store.mail.model.AttachmentId;
import org.apache.james.util.streams.ImmutableCollectors;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.ThrownByLambdaException;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

public class CassandraAttachmentMapper implements AttachmentMapper {

    private final CassandraAsyncExecutor cassandraAsyncExecutor;

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
                .name(Optional.fromNullable(row.getString(NAME)))
                .build();
    }

    @Override
    public List<Attachment> getAttachments(List<AttachmentId> attachmentIds) {
        Preconditions.checkArgument(attachmentIds != null);
        List<String> ids = attachmentIds.stream()
                .map(AttachmentId::getId)
                .collect(ImmutableCollectors.toImmutableList());
        return cassandraAsyncExecutor.execute(
            select(FIELDS)
                .from(TABLE_NAME)
                .where(in(ID, ids)))
            .thenApply(this::attachments)
            .join();
    }

    private List<Attachment> attachments(ResultSet resultSet) {
        Builder<Attachment> builder = ImmutableList.<Attachment> builder();
        resultSet.forEach(row -> builder.add(attachment(row)));
        return builder.build();
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
                .value(PAYLOAD, ByteBuffer.wrap(IOUtils.toByteArray(attachment.getStream())))
                .value(TYPE, attachment.getType())
                .value(NAME, attachment.getName().orNull())
                .value(SIZE, attachment.getSize())
        );
    }

    @Override
    public void storeAttachments(Collection<Attachment> attachments) throws MailboxException {
        try {
            CompletableFuture.allOf(
                    attachments.stream()
                        .map(Throwing.function(this::asyncStoreAttachment))
                        .toArray(CompletableFuture[]::new)
                ).join();
        } catch (ThrownByLambdaException e) {
            throw new MailboxException(e.getCause().getMessage(), e.getCause());
        }
    }
}
