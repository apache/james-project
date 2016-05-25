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

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
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

import org.apache.commons.io.IOUtils;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.exception.AttachmentNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.model.Attachment;
import org.apache.james.mailbox.store.mail.model.AttachmentId;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.google.common.base.Preconditions;

public class CassandraAttachmentMapper implements AttachmentMapper {

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement select;
    private final PreparedStatement insert;

    public CassandraAttachmentMapper(Session session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.select = session.prepare(select(FIELDS)
                .from(TABLE_NAME)
                .where(eq(ID, bindMarker(ID))));
        this.insert = session.prepare(insertInto(TABLE_NAME)
                .value(ID, bindMarker(ID))
                .value(PAYLOAD, bindMarker(PAYLOAD))
                .value(TYPE, bindMarker(TYPE))
                .value(SIZE, bindMarker(SIZE)));
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
        return cassandraAsyncExecutor.executeSingleRow(select.bind()
                .setString(ID, attachmentId.getId()))
                .thenApply(optional -> optional.map(this::attachment))
                .join()
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId.getId()));
    }

    private Attachment attachment(Row row) {
        return Attachment.builder()
                .attachmentId(AttachmentId.from(row.getString(ID)))
                .bytes(row.getBytes(PAYLOAD).array())
                .type(row.getString(TYPE))
                .size(row.getLong(SIZE))
                .build();
    }

    @Override
    public void storeAttachment(Attachment attachment) throws MailboxException {
        try {
            cassandraAsyncExecutor.execute(
                    insert.bind()
                        .setString(ID, attachment.getAttachmentId().getId())
                        .setBytes(PAYLOAD, ByteBuffer.wrap(IOUtils.toByteArray(attachment.getStream())))
                        .setString(TYPE, attachment.getType())
                        .setLong(SIZE, attachment.getSize())
                ).join();
        } catch (IOException e) {
            throw new MailboxException(e.getMessage(), e);
        }
    }
}
