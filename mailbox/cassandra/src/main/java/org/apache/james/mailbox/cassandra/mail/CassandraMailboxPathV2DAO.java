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
import static org.apache.james.mailbox.cassandra.GhostMailbox.TYPE;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathV2Table.FIELDS;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathV2Table.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathV2Table.MAILBOX_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathV2Table.NAMESPACE;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathV2Table.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathV2Table.USER;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.GhostMailbox;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.util.FunctionalUtils;
import org.apache.james.util.ReactorUtils;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraMailboxPathV2DAO implements CassandraMailboxPathDAO {


    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final CassandraUtils cassandraUtils;
    private final PreparedStatement delete;
    private final PreparedStatement insert;
    private final PreparedStatement select;
    private final PreparedStatement selectUser;
    private final PreparedStatement selectAll;

    @Inject
    public CassandraMailboxPathV2DAO(Session session, CassandraUtils cassandraUtils) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.cassandraUtils = cassandraUtils;
        this.insert = prepareInsert(session);
        this.delete = prepareDelete(session);
        this.select = prepareSelect(session);
        this.selectUser = prepareSelectUser(session);
        this.selectAll = prepareSelectAll(session);
    }

    private PreparedStatement prepareDelete(Session session) {
        return session.prepare(QueryBuilder.delete()
            .from(TABLE_NAME)
            .where(eq(NAMESPACE, bindMarker(NAMESPACE)))
            .and(eq(USER, bindMarker(USER)))
            .and(eq(MAILBOX_NAME, bindMarker(MAILBOX_NAME)))
            .ifExists());
    }

    private PreparedStatement prepareInsert(Session session) {
        return session.prepare(insertInto(TABLE_NAME)
            .value(NAMESPACE, bindMarker(NAMESPACE))
            .value(USER, bindMarker(USER))
            .value(MAILBOX_NAME, bindMarker(MAILBOX_NAME))
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .ifNotExists());
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(select(FIELDS)
            .from(TABLE_NAME)
            .where(eq(NAMESPACE, bindMarker(NAMESPACE)))
            .and(eq(USER, bindMarker(USER)))
            .and(eq(MAILBOX_NAME, bindMarker(MAILBOX_NAME))));
    }

    private PreparedStatement prepareSelectUser(Session session) {
        return session.prepare(select(FIELDS)
            .from(TABLE_NAME)
            .where(eq(NAMESPACE, bindMarker(NAMESPACE)))
            .and(eq(USER, bindMarker(USER))));
    }

    private PreparedStatement prepareSelectAll(Session session) {
        return session.prepare(select(FIELDS)
            .from(TABLE_NAME));
    }

    @Override
    public Mono<CassandraIdAndPath> retrieveId(MailboxPath mailboxPath) {
        return cassandraAsyncExecutor.executeSingleRow(
            select.bind()
                .setString(NAMESPACE, mailboxPath.getNamespace())
                .setString(USER, sanitizeUser(mailboxPath.getUser()))
                .setString(MAILBOX_NAME, mailboxPath.getName()))
            .map(this::fromRowToCassandraIdAndPath)
            .map(FunctionalUtils.toFunction(this::logGhostMailboxSuccess))
            .switchIfEmpty(ReactorUtils.executeAndEmpty(() -> logGhostMailboxFailure(mailboxPath)));
    }

    @Override
    public Flux<CassandraIdAndPath> listUserMailboxes(String namespace, Username user) {
        return cassandraAsyncExecutor.execute(
            selectUser.bind()
                .setString(NAMESPACE, namespace)
                .setString(USER, sanitizeUser(user)))
            .flatMapMany(resultSet -> cassandraUtils.convertToFlux(resultSet)
                .map(this::fromRowToCassandraIdAndPath)
                .map(FunctionalUtils.toFunction(this::logReadSuccess)));
    }

    public Flux<CassandraIdAndPath> listAll() {
        return cassandraAsyncExecutor.execute(
            selectAll.bind())
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
            new MailboxPath(row.getString(NAMESPACE),
                Username.of(row.getString(USER)),
                row.getString(MAILBOX_NAME)));
    }

    @Override
    public Mono<Boolean> save(MailboxPath mailboxPath, CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeReturnApplied(insert.bind()
            .setString(NAMESPACE, mailboxPath.getNamespace())
            .setString(USER, sanitizeUser(mailboxPath.getUser()))
            .setString(MAILBOX_NAME, mailboxPath.getName())
            .setUUID(MAILBOX_ID, mailboxId.asUuid()));
    }

    @Override
    public Mono<Void> delete(MailboxPath mailboxPath) {
        return cassandraAsyncExecutor.executeVoid(delete.bind()
            .setString(NAMESPACE, mailboxPath.getNamespace())
            .setString(USER, sanitizeUser(mailboxPath.getUser()))
            .setString(MAILBOX_NAME, mailboxPath.getName()));
    }

    private String sanitizeUser(Username user) {
        if (user == null) {
            return "";
        }
        return user.asString();
    }
}
