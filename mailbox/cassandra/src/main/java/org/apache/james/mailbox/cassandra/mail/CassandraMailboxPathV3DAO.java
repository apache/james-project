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

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles.ConsistencyChoice.STRONG;
import static org.apache.james.mailbox.cassandra.GhostMailbox.TYPE;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathV3Table.FIELDS;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathV3Table.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathV3Table.MAILBOX_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathV3Table.NAMESPACE;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathV3Table.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathV3Table.UIDVALIDITY;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathV3Table.USER;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.init.configuration.JamesExecutionProfiles;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.GhostMailbox;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.util.FunctionalUtils;
import org.apache.james.util.ReactorUtils;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraMailboxPathV3DAO {
    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement delete;
    private final PreparedStatement insert;
    private final PreparedStatement select;
    private final PreparedStatement selectUser;
    private final PreparedStatement selectAll;
    private final CqlSession session;
    private final DriverExecutionProfile lwtProfile;

    @Inject
    public CassandraMailboxPathV3DAO(CqlSession session) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.session = session;
        this.insert = prepareInsert();
        this.delete = prepareDelete();
        this.select = prepareSelect();
        this.selectUser = prepareSelectUser();
        this.selectAll = prepareSelectAll();
        this.lwtProfile = JamesExecutionProfiles.getLWTProfile(session);
    }

    private PreparedStatement prepareDelete() {
        return session.prepare(deleteFrom(TABLE_NAME)
            .where(column(NAMESPACE).isEqualTo(bindMarker(NAMESPACE)),
                column(USER).isEqualTo(bindMarker(USER)),
                column(MAILBOX_NAME).isEqualTo(bindMarker(MAILBOX_NAME)))
            .ifExists()
            .build());
    }

    private PreparedStatement prepareInsert() {
        return session.prepare(insertInto(TABLE_NAME)
            .value(NAMESPACE, bindMarker(NAMESPACE))
            .value(USER, bindMarker(USER))
            .value(MAILBOX_NAME, bindMarker(MAILBOX_NAME))
            .value(MAILBOX_ID, bindMarker(MAILBOX_ID))
            .value(UIDVALIDITY, bindMarker(UIDVALIDITY))
            .ifNotExists()
            .build());
    }

    private PreparedStatement prepareSelect() {
        return session.prepare(selectFrom(TABLE_NAME)
            .columns(MAILBOX_ID, UIDVALIDITY)
            .where(column(NAMESPACE).isEqualTo(bindMarker(NAMESPACE)),
                column(USER).isEqualTo(bindMarker(USER)),
                column(MAILBOX_NAME).isEqualTo(bindMarker(MAILBOX_NAME)))
            .build());
    }

    private PreparedStatement prepareSelectUser() {
        return session.prepare(selectFrom(TABLE_NAME)
            .columns(MAILBOX_ID, UIDVALIDITY, MAILBOX_NAME)
            .where(column(NAMESPACE).isEqualTo(bindMarker(NAMESPACE)),
                column(USER).isEqualTo(bindMarker(USER)))
            .build());
    }

    private PreparedStatement prepareSelectAll() {
        return session.prepare(selectFrom(TABLE_NAME)
            .columns(FIELDS)
            .build());
    }

    public Mono<Mailbox> retrieve(MailboxPath mailboxPath) {
        return retrieve(mailboxPath, STRONG);
    }

    public Mono<Mailbox> retrieve(MailboxPath mailboxPath, JamesExecutionProfiles.ConsistencyChoice consistencyChoice) {
        BoundStatement statement = select.bind()
            .set(NAMESPACE, mailboxPath.getNamespace(), TypeCodecs.TEXT)
            .set(USER, sanitizeUser(mailboxPath.getUser()), TypeCodecs.TEXT)
            .set(MAILBOX_NAME, mailboxPath.getName(), TypeCodecs.TEXT);

        return cassandraAsyncExecutor.executeSingleRow(setExecutionProfileIfNeeded(statement, consistencyChoice))
            .map(row -> fromRow(row, mailboxPath.getUser(), mailboxPath.getNamespace(), mailboxPath.getName()))
            .map(FunctionalUtils.toFunction(this::logGhostMailboxSuccess))
            .switchIfEmpty(ReactorUtils.executeAndEmpty(() -> logGhostMailboxFailure(mailboxPath)));
    }

    public Flux<Mailbox> listUserMailboxes(String namespace, Username user, JamesExecutionProfiles.ConsistencyChoice consistencyChoice) {
        BoundStatementBuilder statementBuilder = selectUser.boundStatementBuilder()
            .set(NAMESPACE, namespace, TypeCodecs.TEXT)
            .set(USER, sanitizeUser(user), TypeCodecs.TEXT);

        if (consistencyChoice.equals(STRONG)) {
            statementBuilder.setExecutionProfile(lwtProfile);
        }

        return cassandraAsyncExecutor.executeRows(statementBuilder.build())
            .map(row -> fromRow(row, user, namespace))
            .map(FunctionalUtils.toFunction(this::logReadSuccess));
    }

    public Flux<Mailbox> listAll() {
        return cassandraAsyncExecutor.executeRows(selectAll.bind())
            .map(this::fromRowToCassandraIdAndPath)
            .map(FunctionalUtils.toFunction(this::logReadSuccess));
    }

    /**
     * See https://issues.apache.org/jira/browse/MAILBOX-322 to read about the Ghost mailbox bug.
     * <p>
     * A missed read on an existing mailbox is the cause of the ghost mailbox bug. Here we log missing reads. Successful
     * reads and write operations are also added in order to allow audit in order to know if the mailbox existed.
     */
    public void logGhostMailboxSuccess(Mailbox value) {
        logReadSuccess(value);
    }

    public void logGhostMailboxFailure(MailboxPath mailboxPath) {
        if (GhostMailbox.isDebugEnabled()) {
            GhostMailbox.logger()
                .field(GhostMailbox.MAILBOX_NAME, mailboxPath.asString())
                .field(TYPE, "readMiss")
                .log(logger -> logger.debug("Read mailbox missed"));
        }
    }

    /**
     * See https://issues.apache.org/jira/browse/MAILBOX-322 to read about the Ghost mailbox bug.
     * <p>
     * Read success allows to know if a mailbox existed before (mailbox write history might be older than this log introduction
     * or log history might have been dropped)
     */
    private void logReadSuccess(Mailbox mailbox) {
        if (GhostMailbox.isDebugEnabled()) {
            GhostMailbox.logger()
                .field(GhostMailbox.MAILBOX_NAME, mailbox.generateAssociatedPath().asString())
                .field(TYPE, "readSuccess")
                .field(GhostMailbox.MAILBOX_ID, mailbox.getMailboxId().serialize())
                .log(logger -> logger.debug("Read mailbox succeeded"));
        }
    }

    private Mailbox fromRowToCassandraIdAndPath(Row row) {
        return fromRow(row, Username.of(row.get(USER, TypeCodecs.TEXT)), row.get(NAMESPACE, TypeCodecs.TEXT));
    }

    private Mailbox fromRow(Row row, Username username, String namespace) {
        return fromRow(row, username, namespace, row.get(MAILBOX_NAME, TypeCodecs.TEXT));
    }

    private Mailbox fromRow(Row row, Username username, String namespace, String name) {
        return new Mailbox(
            new MailboxPath(namespace,
                username,
                name),
            UidValidity.of(row.getLong(UIDVALIDITY)),
            CassandraId.of(row.get(MAILBOX_ID, TypeCodecs.TIMEUUID)));
    }

    public Mono<Boolean> save(Mailbox mailbox) {
        CassandraId id = (CassandraId) mailbox.getMailboxId();

        return cassandraAsyncExecutor.executeReturnApplied(insert.bind()
            .setString(NAMESPACE, mailbox.getNamespace())
            .setString(USER, sanitizeUser(mailbox.getUser()))
            .setLong(UIDVALIDITY, mailbox.getUidValidity().asLong())
            .setString(MAILBOX_NAME, mailbox.getName())
            .setUuid(MAILBOX_ID, id.asUuid()));
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

    private BoundStatement setExecutionProfileIfNeeded(BoundStatement statement, JamesExecutionProfiles.ConsistencyChoice consistencyChoice) {
        if (consistencyChoice.equals(STRONG)) {
            return statement.setExecutionProfile(lwtProfile);
        } else {
            return statement;
        }
    }
}
