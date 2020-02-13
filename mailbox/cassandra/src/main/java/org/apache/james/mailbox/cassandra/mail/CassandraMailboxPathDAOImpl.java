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
import static com.datastax.driver.core.querybuilder.QueryBuilder.count;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.mailbox.cassandra.GhostMailbox.TYPE;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathTable.FIELDS;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathTable.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathTable.MAILBOX_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathTable.NAMESPACE_AND_USER;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathTable.TABLE_NAME;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.GhostMailbox;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.utils.MailboxBaseTupleUtil;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxTable;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.util.FunctionalUtils;
import org.apache.james.util.ReactorUtils;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraMailboxPathDAOImpl implements CassandraMailboxPathDAO {

    private static final int FIRST_CELL = 0;

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final MailboxBaseTupleUtil mailboxBaseTupleUtil;
    private final CassandraUtils cassandraUtils;
    private final PreparedStatement delete;
    private final PreparedStatement insert;
    private final PreparedStatement select;
    private final PreparedStatement selectAllForUser;
    private final PreparedStatement selectAll;
    private final PreparedStatement countAll;

    @Inject
    public CassandraMailboxPathDAOImpl(Session session, CassandraTypesProvider typesProvider, CassandraUtils cassandraUtils) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.mailboxBaseTupleUtil = new MailboxBaseTupleUtil(typesProvider);
        this.cassandraUtils = cassandraUtils;
        this.insert = prepareInsert(session);
        this.delete = prepareDelete(session);
        this.select = prepareSelect(session);
        this.selectAllForUser = prepareSelectAllForUser(session);
        this.selectAll = prepareSelectAll(session);
        this.countAll = prepareCountAll(session);
    }

    @VisibleForTesting
    public CassandraMailboxPathDAOImpl(Session session, CassandraTypesProvider typesProvider) {
        this(session, typesProvider, CassandraUtils.WITH_DEFAULT_CONFIGURATION);
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(QueryBuilder.delete()
            .from(TABLE_NAME)
            .where(eq(NAMESPACE_AND_USER, bindMarker(NAMESPACE_AND_USER)))
            .and(eq(MAILBOX_NAME, bindMarker(MAILBOX_NAME))));
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(NAMESPACE_AND_USER, bindMarker(NAMESPACE_AND_USER))
            .value(MAILBOX_NAME, bindMarker(MAILBOX_NAME))
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .ifNotExists());
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select(FIELDS)
            .from(TABLE_NAME)
            .where(eq(NAMESPACE_AND_USER, bindMarker(NAMESPACE_AND_USER)))
            .and(eq(MAILBOX_NAME, bindMarker(MAILBOX_NAME))));
    }

    private PreparedStatement prepareSelectAllForUser(Session session) {
        return session.prepare(select(FIELDS)
            .from(TABLE_NAME)
            .where(eq(NAMESPACE_AND_USER, bindMarker(NAMESPACE_AND_USER))));
    }

    private PreparedStatement prepareSelectAll(Session session) {
        return session.prepare(select(FIELDS)
            .from(TABLE_NAME));
    }

    private PreparedStatement prepareCountAll(Session session) {
        return session.prepare(select(count(NAMESPACE_AND_USER))
            .from(TABLE_NAME));
    }

    public Mono<CassandraIdAndPath> retrieveId(MailboxPath mailboxPath) {
        return cassandraAsyncExecutor.executeSingleRow(
            select.bind()
                .setUDTValue(NAMESPACE_AND_USER, mailboxBaseTupleUtil.createMailboxBaseUDT(mailboxPath.getNamespace(), mailboxPath.getUser()))
                .setString(MAILBOX_NAME, mailboxPath.getName()))
            .map(this::fromRowToCassandraIdAndPath)
            .map(FunctionalUtils.toFunction(this::logGhostMailboxSuccess))
            .switchIfEmpty(ReactorUtils.executeAndEmpty(() -> logGhostMailboxFailure(mailboxPath)));
    }

    @Override
    public Flux<CassandraIdAndPath> listUserMailboxes(String namespace, Username user) {
        return cassandraAsyncExecutor.execute(
            selectAllForUser.bind()
                .setUDTValue(NAMESPACE_AND_USER, mailboxBaseTupleUtil.createMailboxBaseUDT(namespace, user)))
            .flatMapMany(resultSet -> cassandraUtils.convertToFlux(resultSet)
                .map(this::fromRowToCassandraIdAndPath)
                .map(FunctionalUtils.toFunction(this::logReadSuccess)));
    }

    /**
     * See https://issues.apache.org/jira/browse/MAILBOX-322 to read about the Ghost mailbox bug.
     *
     * A missed read on an existing mailbox is the cause of the ghost mailbox bug. Here we log missing reads. Successful
     * reads and write operations are also added in order to allow audit in order to know if the mailbox existed.
     */
    @Override
    public void logGhostMailboxSuccess(CassandraIdAndPath value) {
        logReadSuccess(value);
    }

    @Override
    public void logGhostMailboxFailure(MailboxPath mailboxPath) {
        GhostMailbox.logger()
                .addField(GhostMailbox.MAILBOX_NAME, mailboxPath)
                .addField(TYPE, "readMiss")
                .log(logger -> logger.debug("Read mailbox missed"));
    }


    /**
     * See https://issues.apache.org/jira/browse/MAILBOX-322 to read about the Ghost mailbox bug.
     *
     * Read success allows to know if a mailbox existed before (mailbox write history might be older than this log introduction
     * or log history might have been dropped)
     */
    private void logReadSuccess(CassandraIdAndPath cassandraIdAndPath) {
        GhostMailbox.logger()
            .addField(GhostMailbox.MAILBOX_NAME, cassandraIdAndPath.getMailboxPath())
            .addField(TYPE, "readSuccess")
            .addField(GhostMailbox.MAILBOX_ID, cassandraIdAndPath.getCassandraId())
            .log(logger -> logger.debug("Read mailbox succeeded"));
    }

    private CassandraIdAndPath fromRowToCassandraIdAndPath(Row row) {
        return new CassandraIdAndPath(
            CassandraId.of(row.getUUID(MAILBOX_ID)),
            new MailboxPath(row.getUDTValue(NAMESPACE_AND_USER).getString(CassandraMailboxTable.MailboxBase.NAMESPACE),
                Username.of(row.getUDTValue(NAMESPACE_AND_USER).getString(CassandraMailboxTable.MailboxBase.USER)),
                row.getString(MAILBOX_NAME)));
    }

    @Override
    public Mono<Boolean> save(MailboxPath mailboxPath, CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeReturnApplied(insert.bind()
            .setUDTValue(NAMESPACE_AND_USER, mailboxBaseTupleUtil.createMailboxBaseUDT(mailboxPath.getNamespace(), mailboxPath.getUser()))
            .setString(MAILBOX_NAME, mailboxPath.getName())
            .setUUID(MAILBOX_ID, mailboxId.asUuid()));
    }

    @Override
    public Mono<Void> delete(MailboxPath mailboxPath) {
        return cassandraAsyncExecutor.executeVoid(delete.bind()
            .setUDTValue(NAMESPACE_AND_USER, mailboxBaseTupleUtil.createMailboxBaseUDT(mailboxPath.getNamespace(), mailboxPath.getUser()))
            .setString(MAILBOX_NAME, mailboxPath.getName()));
    }

    public Flux<CassandraIdAndPath> readAll() {
        return cassandraAsyncExecutor.executeRows(selectAll.bind())
            .map(this::fromRowToCassandraIdAndPath);
    }

    public Mono<Long> countAll() {
        return cassandraAsyncExecutor.executeSingleRowOptional(countAll.bind())
            .map(optional -> optional.map(row -> row.getLong(FIRST_CELL)).orElse(0L));
    }

}
