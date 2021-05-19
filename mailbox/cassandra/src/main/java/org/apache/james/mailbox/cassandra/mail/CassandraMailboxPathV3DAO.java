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
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathV3Table.FIELDS;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathV3Table.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathV3Table.MAILBOX_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathV3Table.NAMESPACE;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathV3Table.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathV3Table.UIDVALIDITY;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathV3Table.USER;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.init.configuration.CassandraConsistenciesConfiguration;
import org.apache.james.backends.cassandra.init.configuration.CassandraConsistenciesConfiguration.ConsistencyChoice;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.GhostMailbox;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.util.FunctionalUtils;
import org.apache.james.util.ReactorUtils;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraMailboxPathV3DAO {
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final CassandraUtils cassandraUtils;
    private final PreparedStatement delete;
    private final PreparedStatement insert;
    private final PreparedStatement select;
    private final PreparedStatement selectUser;
    private final PreparedStatement selectAll;
    private final CassandraConsistenciesConfiguration consistenciesConfiguration;

    @Inject
    public CassandraMailboxPathV3DAO(Session session, CassandraUtils cassandraUtils,
                                     CassandraConsistenciesConfiguration consistenciesConfiguration) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.consistenciesConfiguration = consistenciesConfiguration;
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
            .value(UIDVALIDITY, bindMarker(UIDVALIDITY))
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

    public Mono<Mailbox> retrieve(MailboxPath mailboxPath) {
        return retrieve(mailboxPath, consistenciesConfiguration.getLightweightTransaction());
    }

    public Mono<Mailbox> retrieve(MailboxPath mailboxPath, ConsistencyChoice consistencyChoice) {
        return retrieve(mailboxPath, consistencyChoice.choose(consistenciesConfiguration));
    }

    private Mono<Mailbox> retrieve(MailboxPath mailboxPath, ConsistencyLevel consistencyLevel) {
        return cassandraAsyncExecutor.executeSingleRow(
            select.bind()
                .setString(NAMESPACE, mailboxPath.getNamespace())
                .setString(USER, sanitizeUser(mailboxPath.getUser()))
                .setString(MAILBOX_NAME, mailboxPath.getName())
                .setConsistencyLevel(consistencyLevel))
            .map(this::fromRowToCassandraIdAndPath)
            .map(FunctionalUtils.toFunction(this::logGhostMailboxSuccess))
            .switchIfEmpty(ReactorUtils.executeAndEmpty(() -> logGhostMailboxFailure(mailboxPath)));
    }

    public Flux<Mailbox> listUserMailboxes(String namespace, Username user, ConsistencyChoice consistencyChoice) {
        return cassandraAsyncExecutor.execute(
            selectUser.bind()
                .setString(NAMESPACE, namespace)
                .setString(USER, sanitizeUser(user))
                .setConsistencyLevel(consistencyChoice.choose(consistenciesConfiguration)))
            .flatMapMany(cassandraUtils::convertToFlux)
            .map(this::fromRowToCassandraIdAndPath)
            .map(FunctionalUtils.toFunction(this::logReadSuccess));
    }

    public Flux<Mailbox> listAll() {
        return cassandraAsyncExecutor.execute(
            selectAll.bind())
            .flatMapMany(cassandraUtils::convertToFlux)
            .map(this::fromRowToCassandraIdAndPath)
            .map(FunctionalUtils.toFunction(this::logReadSuccess));
    }

    /**
     * See https://issues.apache.org/jira/browse/MAILBOX-322 to read about the Ghost mailbox bug.
     *
     * A missed read on an existing mailbox is the cause of the ghost mailbox bug. Here we log missing reads. Successful
     * reads and write operations are also added in order to allow audit in order to know if the mailbox existed.
     */
    public void logGhostMailboxSuccess(Mailbox value) {
        logReadSuccess(value);
    }

    public void logGhostMailboxFailure(MailboxPath mailboxPath) {
        GhostMailbox.logger()
                .field(GhostMailbox.MAILBOX_NAME, mailboxPath.asString())
                .field(TYPE, "readMiss")
                .log(logger -> logger.debug("Read mailbox missed"));
    }

    /**
     * See https://issues.apache.org/jira/browse/MAILBOX-322 to read about the Ghost mailbox bug.
     *
     * Read success allows to know if a mailbox existed before (mailbox write history might be older than this log introduction
     * or log history might have been dropped)
     */
    private void logReadSuccess(Mailbox mailbox) {
        GhostMailbox.logger()
            .field(GhostMailbox.MAILBOX_NAME, mailbox.generateAssociatedPath().asString())
            .field(TYPE, "readSuccess")
            .field(GhostMailbox.MAILBOX_ID, mailbox.getMailboxId().serialize())
            .log(logger -> logger.debug("Read mailbox succeeded"));
    }

    private Mailbox fromRowToCassandraIdAndPath(Row row) {
        return new Mailbox(
            new MailboxPath(row.getString(NAMESPACE),
                Username.of(row.getString(USER)),
                row.getString(MAILBOX_NAME)),
            UidValidity.of(row.getLong(UIDVALIDITY)),
            CassandraId.of(row.getUUID(MAILBOX_ID)));
    }

    public Mono<Boolean> save(Mailbox mailbox) {
        CassandraId id = (CassandraId) mailbox.getMailboxId();

        return cassandraAsyncExecutor.executeReturnApplied(insert.bind()
            .setString(NAMESPACE, mailbox.getNamespace())
            .setString(USER, sanitizeUser(mailbox.getUser()))
            .setLong(UIDVALIDITY, mailbox.getUidValidity().asLong())
            .setString(MAILBOX_NAME, mailbox.getName())
            .setUUID(MAILBOX_ID, id.asUuid()));
    }

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
