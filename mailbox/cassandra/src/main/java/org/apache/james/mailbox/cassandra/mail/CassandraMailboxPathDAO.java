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
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathTable.FIELDS;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathTable.MAILBOX_ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathTable.MAILBOX_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathTable.NAMESPACE_AND_USER;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxPathTable.TABLE_NAME;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.utils.MailboxBaseTupleUtil;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxTable;
import org.apache.james.mailbox.model.MailboxPath;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;

public class CassandraMailboxPathDAO {

    public static class CassandraIdAndPath {
        private final CassandraId cassandraId;
        private final MailboxPath mailboxPath;

        public CassandraIdAndPath(CassandraId cassandraId, MailboxPath mailboxPath) {
            this.cassandraId = cassandraId;
            this.mailboxPath = mailboxPath;
        }

        public CassandraId getCassandraId() {
            return cassandraId;
        }

        public MailboxPath getMailboxPath() {
            return mailboxPath;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof CassandraIdAndPath) {
                CassandraIdAndPath that = (CassandraIdAndPath) o;

                return Objects.equal(this.cassandraId, that.cassandraId)
                    && Objects.equal(this.mailboxPath, that.mailboxPath);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hashCode(cassandraId, mailboxPath);
        }
    }

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final MailboxBaseTupleUtil mailboxBaseTupleUtil;
    private final CassandraUtils cassandraUtils;
    private final PreparedStatement delete;
    private final PreparedStatement insert;
    private final PreparedStatement select;
    private final PreparedStatement selectAll;

    @Inject
    public CassandraMailboxPathDAO(Session session, CassandraTypesProvider typesProvider, CassandraUtils cassandraUtils) {
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);
        this.mailboxBaseTupleUtil = new MailboxBaseTupleUtil(typesProvider);
        this.cassandraUtils = cassandraUtils;
        this.insert = prepareInsert(session);
        this.delete = prepareDelete(session);
        this.select = prepareSelect(session);
        this.selectAll = prepareSelectAll(session);
    }

    @VisibleForTesting
    public CassandraMailboxPathDAO(Session session, CassandraTypesProvider typesProvider) {
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

    private PreparedStatement prepareSelectAll(Session session) {
        return session.prepare(select(FIELDS)
            .from(TABLE_NAME)
            .where(eq(NAMESPACE_AND_USER, bindMarker(NAMESPACE_AND_USER))));
    }

    public CompletableFuture<Optional<CassandraIdAndPath>> retrieveId(MailboxPath mailboxPath) {
        return cassandraAsyncExecutor.executeSingleRow(
            select.bind()
                .setUDTValue(NAMESPACE_AND_USER, mailboxBaseTupleUtil.createMailboxBaseUDT(mailboxPath.getNamespace(), mailboxPath.getUser()))
                .setString(MAILBOX_NAME, mailboxPath.getName()))
            .thenApply(rowOptional ->
                rowOptional.map(row -> new CassandraIdAndPath(
                    CassandraId.of(row.getUUID(MAILBOX_ID)),
                    mailboxPath)));
    }

    public CompletableFuture<Stream<CassandraIdAndPath>> listUserMailboxes(String namespace, String user) {
        return cassandraAsyncExecutor.execute(
            selectAll.bind()
                .setUDTValue(NAMESPACE_AND_USER, mailboxBaseTupleUtil.createMailboxBaseUDT(namespace, user)))
            .thenApply(resultSet -> cassandraUtils.convertToStream(resultSet).map(this::fromRowToCassandraIdAndPath));
    }

    private CassandraIdAndPath fromRowToCassandraIdAndPath(Row row) {
        return new CassandraIdAndPath(
            CassandraId.of(row.getUUID(MAILBOX_ID)),
            new MailboxPath(row.getUDTValue(NAMESPACE_AND_USER).getString(CassandraMailboxTable.MailboxBase.NAMESPACE),
                row.getUDTValue(NAMESPACE_AND_USER).getString(CassandraMailboxTable.MailboxBase.USER),
                row.getString(MAILBOX_NAME)));
    }

    public CompletableFuture<Boolean> save(MailboxPath mailboxPath, CassandraId mailboxId) {
        return cassandraAsyncExecutor.executeReturnApplied(insert.bind()
            .setUDTValue(NAMESPACE_AND_USER, mailboxBaseTupleUtil.createMailboxBaseUDT(mailboxPath.getNamespace(), mailboxPath.getUser()))
            .setString(MAILBOX_NAME, mailboxPath.getName())
            .setUUID(MAILBOX_ID, mailboxId.asUuid()));
    }

    public CompletableFuture<Void> delete(MailboxPath mailboxPath) {
        return cassandraAsyncExecutor.executeVoid(delete.bind()
            .setUDTValue(NAMESPACE_AND_USER, mailboxBaseTupleUtil.createMailboxBaseUDT(mailboxPath.getNamespace(), mailboxPath.getUser()))
            .setString(MAILBOX_NAME, mailboxPath.getName()));
    }

}
