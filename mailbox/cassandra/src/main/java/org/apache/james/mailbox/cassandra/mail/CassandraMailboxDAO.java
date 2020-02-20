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
import static com.datastax.driver.core.querybuilder.QueryBuilder.set;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.FIELDS;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.MAILBOX_BASE;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.UIDVALIDITY;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.utils.MailboxBaseTupleUtil;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxTable;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraMailboxDAO {

    private final CassandraAsyncExecutor executor;
    private final MailboxBaseTupleUtil mailboxBaseTupleUtil;
    private final CassandraUtils cassandraUtils;
    private final PreparedStatement readStatement;
    private final PreparedStatement listStatement;
    private final PreparedStatement deleteStatement;
    private final PreparedStatement insertStatement;
    private final PreparedStatement updateStatement;

    @Inject
    public CassandraMailboxDAO(Session session, CassandraTypesProvider typesProvider, CassandraUtils cassandraUtils) {
        this.executor = new CassandraAsyncExecutor(session);
        this.mailboxBaseTupleUtil = new MailboxBaseTupleUtil(typesProvider);
        this.insertStatement = prepareInsert(session);
        this.updateStatement = prepareUpdate(session);
        this.deleteStatement = prepareDelete(session);
        this.listStatement = prepareList(session);
        this.readStatement = prepareRead(session);
        this.cassandraUtils = cassandraUtils;
    }

    @VisibleForTesting
    public CassandraMailboxDAO(Session session, CassandraTypesProvider typesProvider) {
        this(session, typesProvider, CassandraUtils.WITH_DEFAULT_CONFIGURATION);
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(ID, bindMarker(ID))
            .value(NAME, bindMarker(NAME))
            .value(UIDVALIDITY, bindMarker(UIDVALIDITY))
            .value(MAILBOX_BASE, bindMarker(MAILBOX_BASE)));
    }

    private PreparedStatement prepareUpdate(Session session) {
        return session.prepare(update(TABLE_NAME)
            .with(set(MAILBOX_BASE, bindMarker(MAILBOX_BASE)))
            .and(set(NAME, bindMarker(NAME)))
            .where(eq(ID, bindMarker(ID))));
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(QueryBuilder.delete()
            .from(TABLE_NAME)
            .where(eq(ID, bindMarker(ID))));
    }

    private PreparedStatement prepareList(Session session) {
        return session.prepare(select(FIELDS).from(TABLE_NAME));
    }

    private PreparedStatement prepareRead(Session session) {
        return session.prepare(select(FIELDS).from(TABLE_NAME)
            .where(eq(ID, bindMarker(ID))));
    }

    public Mono<Void> save(Mailbox mailbox) {
        CassandraId cassandraId = (CassandraId) mailbox.getMailboxId();
        return executor.executeVoid(insertStatement.bind()
            .setUUID(ID, cassandraId.asUuid())
            .setString(NAME, mailbox.getName())
            .setLong(UIDVALIDITY, mailbox.getUidValidity())
            .setUDTValue(MAILBOX_BASE, mailboxBaseTupleUtil.createMailboxBaseUDT(mailbox.getNamespace(), mailbox.getUser())));
    }

    public Mono<Void> updatePath(CassandraId mailboxId, MailboxPath mailboxPath) {
        return executor.executeVoid(updateStatement.bind()
            .setUUID(ID, mailboxId.asUuid())
            .setString(NAME, mailboxPath.getName())
            .setUDTValue(MAILBOX_BASE, mailboxBaseTupleUtil.createMailboxBaseUDT(mailboxPath.getNamespace(), mailboxPath.getUser())));
    }

    public Mono<Void> delete(CassandraId mailboxId) {
        return executor.executeVoid(deleteStatement.bind()
            .setUUID(ID, mailboxId.asUuid()));
    }

    public Mono<Mailbox> retrieveMailbox(CassandraId mailboxId) {
        return executor.executeSingleRow(readStatement.bind()
            .setUUID(ID, mailboxId.asUuid()))
            .map(row -> mailboxFromRow(row, mailboxId));
    }

    private Mailbox mailboxFromRow(Row row, CassandraId cassandraId) {
        return new Mailbox(
            new MailboxPath(
                row.getUDTValue(MAILBOX_BASE).getString(CassandraMailboxTable.MailboxBase.NAMESPACE),
                Username.of(row.getUDTValue(MAILBOX_BASE).getString(CassandraMailboxTable.MailboxBase.USER)),
                row.getString(NAME)),
            row.getLong(UIDVALIDITY),
            cassandraId);
    }

    public Flux<Mailbox> retrieveAllMailboxes() {
        return executor.execute(listStatement.bind())
            .flatMapMany(cassandraUtils::convertToFlux)
            .map(this::toMailboxWithId);
    }

    private Mailbox toMailboxWithId(Row row) {
        return mailboxFromRow(row, CassandraId.of(row.getUUID(ID)));
    }

}
