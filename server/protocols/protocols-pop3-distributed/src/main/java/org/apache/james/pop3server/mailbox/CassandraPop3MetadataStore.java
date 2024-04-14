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

import static com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder.ASC;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;

import java.util.function.Function;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.reactivestreams.Publisher;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;

public class CassandraPop3MetadataStore implements Pop3MetadataStore {

    private final CassandraAsyncExecutor executor;

    private final PreparedStatement list;
    private final PreparedStatement listAll;
    private final PreparedStatement select;
    private final PreparedStatement add;
    private final PreparedStatement remove;
    private final PreparedStatement clear;
    private final DriverExecutionProfile batchProfile;

    @Inject
    public CassandraPop3MetadataStore(CqlSession session) {
        this.executor = new CassandraAsyncExecutor(session);
        this.clear = prepareClear(session);
        this.list = prepareList(session);
        this.listAll = prepareListAll(session);
        this.select = prepareSelect(session);
        this.add = prepareAdd(session);
        this.remove = prepareRemove(session);

        batchProfile = JamesExecutionProfiles.getBatchProfile(session);
    }

    private PreparedStatement prepareRemove(CqlSession session) {
        return session.prepare(deleteFrom(Pop3MetadataModule.TABLE_NAME)
            .whereColumn(Pop3MetadataModule.MAILBOX_ID).isEqualTo(bindMarker(Pop3MetadataModule.MAILBOX_ID))
            .whereColumn(Pop3MetadataModule.MESSAGE_ID).isEqualTo(bindMarker(Pop3MetadataModule.MESSAGE_ID))
            .build());
    }

    private PreparedStatement prepareClear(CqlSession session) {
        return session.prepare(deleteFrom(Pop3MetadataModule.TABLE_NAME)
            .whereColumn(Pop3MetadataModule.MAILBOX_ID).isEqualTo(bindMarker(Pop3MetadataModule.MAILBOX_ID))
            .build());
    }

    private PreparedStatement prepareAdd(CqlSession session) {
        return session.prepare(insertInto(Pop3MetadataModule.TABLE_NAME)
            .value(Pop3MetadataModule.MAILBOX_ID, bindMarker(Pop3MetadataModule.MAILBOX_ID))
            .value(Pop3MetadataModule.MESSAGE_ID, bindMarker(Pop3MetadataModule.MESSAGE_ID))
            .value(Pop3MetadataModule.SIZE, bindMarker(Pop3MetadataModule.SIZE))
            .build());
    }

    private PreparedStatement prepareList(CqlSession session) {
        return session.prepare(selectFrom(Pop3MetadataModule.TABLE_NAME)
            .all()
            .whereColumn(Pop3MetadataModule.MAILBOX_ID).isEqualTo(bindMarker(Pop3MetadataModule.MAILBOX_ID))
            .orderBy(Pop3MetadataModule.MESSAGE_ID, ASC)
            .build());
    }

    private PreparedStatement prepareListAll(CqlSession session) {
        return session.prepare(selectFrom(Pop3MetadataModule.TABLE_NAME).all().build());
    }

    private PreparedStatement prepareSelect(CqlSession session) {
        return session.prepare(selectFrom(Pop3MetadataModule.TABLE_NAME)
            .all()
            .whereColumn(Pop3MetadataModule.MAILBOX_ID).isEqualTo(bindMarker(Pop3MetadataModule.MAILBOX_ID))
            .whereColumn(Pop3MetadataModule.MESSAGE_ID).isEqualTo(bindMarker(Pop3MetadataModule.MESSAGE_ID))
            .build());
    }

    @Override
    public Publisher<StatMetadata> stat(MailboxId mailboxId) {
        CassandraId id = (CassandraId) mailboxId;

        return executor.executeRows(list.bind()
                .setUuid(Pop3MetadataModule.MAILBOX_ID, id.asUuid()))
            .map(row -> new StatMetadata(
                CassandraMessageId.Factory.of(row.getUuid(Pop3MetadataModule.MESSAGE_ID)),
                row.getLong(Pop3MetadataModule.SIZE)));
    }

    @Override
    public Publisher<FullMetadata> listAllEntries() {
        return executor.executeRows(listAll.bind()
            .setExecutionProfile(batchProfile))
            .map(rowToFullMetadataFunction());
    }

    @Override
    public Publisher<FullMetadata> retrieve(MailboxId mailboxId, MessageId messageId) {
        CassandraId id = (CassandraId) mailboxId;
        CassandraMessageId cassandraMessageId = (CassandraMessageId) messageId;
        return executor.executeRows(select.bind()
            .setUuid(Pop3MetadataModule.MAILBOX_ID, id.asUuid())
            .setUuid(Pop3MetadataModule.MESSAGE_ID, cassandraMessageId.get()))
            .map(rowToFullMetadataFunction());
    }

    @Override
    public Publisher<Void> add(MailboxId mailboxId, StatMetadata statMetadata) {
        CassandraId id = (CassandraId) mailboxId;
        CassandraMessageId messageId = (CassandraMessageId) statMetadata.getMessageId();

        return executor.executeVoid(add.bind()
            .setUuid(Pop3MetadataModule.MAILBOX_ID, id.asUuid())
            .setUuid(Pop3MetadataModule.MESSAGE_ID, messageId.get())
            .setLong(Pop3MetadataModule.SIZE, statMetadata.getSize()));
    }

    @Override
    public Publisher<Void> remove(MailboxId mailboxId, MessageId messageId) {
        CassandraId id = (CassandraId) mailboxId;
        CassandraMessageId cassandraMessageId = (CassandraMessageId) messageId;

        return executor.executeVoid(remove.bind()
            .setUuid(Pop3MetadataModule.MAILBOX_ID, id.asUuid())
            .setUuid(Pop3MetadataModule.MESSAGE_ID, cassandraMessageId.get()));
    }

    @Override
    public Publisher<Void> clear(MailboxId mailboxId) {
        CassandraId id = (CassandraId) mailboxId;

        return executor.executeVoid(clear.bind()
            .setUuid(Pop3MetadataModule.MAILBOX_ID, id.asUuid()));
    }

    private Function<Row, FullMetadata> rowToFullMetadataFunction() {
        return row -> new FullMetadata(
            CassandraId.of(row.getUuid(Pop3MetadataModule.MAILBOX_ID)),
            CassandraMessageId.Factory.of(row.getUuid(Pop3MetadataModule.MESSAGE_ID)),
            row.getLong(Pop3MetadataModule.SIZE));
    }
}
