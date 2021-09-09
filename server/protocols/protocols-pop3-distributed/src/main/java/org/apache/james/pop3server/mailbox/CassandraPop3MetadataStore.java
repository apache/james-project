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

package org.apache.james.pop3server.mailbox;

import static com.datastax.driver.core.querybuilder.QueryBuilder.asc;
import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;

import java.util.function.Function;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.reactivestreams.Publisher;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;

public class CassandraPop3MetadataStore implements Pop3MetadataStore {
    private static final int LIST_ALL_TIME_OUT_MILLIS = 3600000;

    private final CassandraAsyncExecutor executor;

    private final PreparedStatement list;
    private final PreparedStatement listAll;
    private final PreparedStatement select;
    private final PreparedStatement add;
    private final PreparedStatement remove;
    private final PreparedStatement clear;

    @Inject
    public CassandraPop3MetadataStore(Session session) {
        this.executor = new CassandraAsyncExecutor(session);
        this.clear = prepareClear(session);
        this.list = prepareList(session);
        this.listAll = prepareListAll(session);
        this.select = prepareSelect(session);
        this.add = prepareAdd(session);
        this.remove = prepareRemove(session);
    }

    private PreparedStatement prepareRemove(Session session) {
        return session.prepare(delete()
            .from(Pop3MetadataModule.TABLE_NAME)
            .where(QueryBuilder.eq(Pop3MetadataModule.MAILBOX_ID, bindMarker(Pop3MetadataModule.MAILBOX_ID)))
            .and(QueryBuilder.eq(Pop3MetadataModule.MESSAGE_ID, bindMarker(Pop3MetadataModule.MESSAGE_ID))));
    }

    private PreparedStatement prepareClear(Session session) {
        return session.prepare(delete()
            .from(Pop3MetadataModule.TABLE_NAME)
            .where(QueryBuilder.eq(Pop3MetadataModule.MAILBOX_ID, bindMarker(Pop3MetadataModule.MAILBOX_ID))));
    }

    private PreparedStatement prepareAdd(Session session) {
        return session.prepare(QueryBuilder.insertInto(Pop3MetadataModule.TABLE_NAME)
            .value(Pop3MetadataModule.MAILBOX_ID, bindMarker(Pop3MetadataModule.MAILBOX_ID))
            .value(Pop3MetadataModule.MESSAGE_ID, bindMarker(Pop3MetadataModule.MESSAGE_ID))
            .value(Pop3MetadataModule.SIZE, bindMarker(Pop3MetadataModule.SIZE)));
    }

    private PreparedStatement prepareList(Session session) {
        return session.prepare(select()
            .from(Pop3MetadataModule.TABLE_NAME)
            .where(QueryBuilder.eq(Pop3MetadataModule.MAILBOX_ID, bindMarker(Pop3MetadataModule.MAILBOX_ID)))
            .orderBy(asc(Pop3MetadataModule.MESSAGE_ID)));
    }

    private PreparedStatement prepareListAll(Session session) {
        return session.prepare(select()
            .from(Pop3MetadataModule.TABLE_NAME));
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select()
            .from(Pop3MetadataModule.TABLE_NAME)
            .where(QueryBuilder.eq(Pop3MetadataModule.MAILBOX_ID, bindMarker(Pop3MetadataModule.MAILBOX_ID)))
            .and(QueryBuilder.eq(Pop3MetadataModule.MESSAGE_ID, bindMarker(Pop3MetadataModule.MESSAGE_ID))));
    }

    @Override
    public Publisher<StatMetadata> stat(MailboxId mailboxId) {
        CassandraId id = (CassandraId) mailboxId;

        return executor.executeRows(list.bind()
                .setUUID(Pop3MetadataModule.MAILBOX_ID, id.asUuid()))
            .map(row -> new StatMetadata(
                CassandraMessageId.Factory.of(row.getUUID(Pop3MetadataModule.MESSAGE_ID)),
                row.getLong(Pop3MetadataModule.SIZE)));
    }

    @Override
    public Publisher<FullMetadata> listAllEntries() {
        return executor.executeRows(listAll.bind()
            .setReadTimeoutMillis(LIST_ALL_TIME_OUT_MILLIS))
            .map(rowToFullMetadataFunction());
    }

    @Override
    public Publisher<FullMetadata> retrieve(MailboxId mailboxId, MessageId messageId) {
        CassandraId id = (CassandraId) mailboxId;
        CassandraMessageId cassandraMessageId = (CassandraMessageId) messageId;
        return executor.executeRows(select.bind()
            .setUUID(Pop3MetadataModule.MAILBOX_ID, id.asUuid())
            .setUUID(Pop3MetadataModule.MESSAGE_ID, cassandraMessageId.get()))
            .map(rowToFullMetadataFunction());
    }

    @Override
    public Publisher<Void> add(MailboxId mailboxId, StatMetadata statMetadata) {
        CassandraId id = (CassandraId) mailboxId;
        CassandraMessageId messageId = (CassandraMessageId) statMetadata.getMessageId();

        return executor.executeVoid(add.bind()
            .setUUID(Pop3MetadataModule.MAILBOX_ID, id.asUuid())
            .setUUID(Pop3MetadataModule.MESSAGE_ID, messageId.get())
            .setLong(Pop3MetadataModule.SIZE, statMetadata.getSize()));
    }

    @Override
    public Publisher<Void> remove(MailboxId mailboxId, MessageId messageId) {
        CassandraId id = (CassandraId) mailboxId;
        CassandraMessageId cassandraMessageId = (CassandraMessageId) messageId;

        return executor.executeVoid(remove.bind()
            .setUUID(Pop3MetadataModule.MAILBOX_ID, id.asUuid())
            .setUUID(Pop3MetadataModule.MESSAGE_ID, cassandraMessageId.get()));
    }

    @Override
    public Publisher<Void> clear(MailboxId mailboxId) {
        CassandraId id = (CassandraId) mailboxId;

        return executor.executeVoid(clear.bind()
            .setUUID(Pop3MetadataModule.MAILBOX_ID, id.asUuid()));
    }

    private Function<Row, FullMetadata> rowToFullMetadataFunction() {
        return row -> new FullMetadata(
            CassandraId.of(row.getUUID(Pop3MetadataModule.MAILBOX_ID)),
            CassandraMessageId.Factory.of(row.getUUID(Pop3MetadataModule.MESSAGE_ID)),
            row.getLong(Pop3MetadataModule.SIZE));
    }
}
