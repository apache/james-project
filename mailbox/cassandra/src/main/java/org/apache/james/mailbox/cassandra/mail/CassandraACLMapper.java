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

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.init.CassandraConfiguration;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.FunctionRunnerWithRetry;
import org.apache.james.backends.cassandra.utils.LightweightTransactionException;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.table.CassandraACLTable;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxTable;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.store.json.MailboxACLJsonConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Throwables;

public class CassandraACLMapper {
    public static final int INITIAL_VALUE = 0;
    private static final Logger LOG = LoggerFactory.getLogger(CassandraACLMapper.class);
    private static final String OLD_VERSION = "oldVersion";

    @FunctionalInterface
    public interface CodeInjector {
        void inject();
    }

    private final CassandraAsyncExecutor executor;
    private final int maxRetry;
    private final CodeInjector codeInjector;
    private final PreparedStatement conditionalInsertStatement;
    private final PreparedStatement conditionalUpdateStatement;
    private final PreparedStatement readStatement;

    @Inject
    public CassandraACLMapper(Session session, CassandraConfiguration cassandraConfiguration) {
        this(session, cassandraConfiguration, () -> {});
    }

    public CassandraACLMapper(Session session, CassandraConfiguration cassandraConfiguration, CodeInjector codeInjector) {
        this.executor = new CassandraAsyncExecutor(session);
        this.maxRetry = cassandraConfiguration.getAclMaxRetry();
        this.codeInjector = codeInjector;
        this.conditionalInsertStatement = prepareConditionalInsert(session);
        this.conditionalUpdateStatement = prepareConditionalUpdate(session);
        this.readStatement = prepareReadStatement(session);
    }

    private PreparedStatement prepareConditionalInsert(Session session) {
        return session.prepare(
            insertInto(CassandraACLTable.TABLE_NAME)
                .value(CassandraACLTable.ID, bindMarker(CassandraACLTable.ID))
                .value(CassandraACLTable.ACL, bindMarker(CassandraACLTable.ACL))
                .value(CassandraACLTable.VERSION, INITIAL_VALUE)
                .ifNotExists());
    }

    private PreparedStatement prepareConditionalUpdate(Session session) {
        return session.prepare(
            update(CassandraACLTable.TABLE_NAME)
                .where(eq(CassandraACLTable.ID, bindMarker(CassandraACLTable.ID)))
                .with(set(CassandraACLTable.ACL, bindMarker(CassandraACLTable.ACL)))
                .and(set(CassandraACLTable.VERSION, bindMarker(CassandraACLTable.VERSION)))
                .onlyIf(eq(CassandraACLTable.VERSION, bindMarker(OLD_VERSION))));
    }

    private PreparedStatement prepareReadStatement(Session session) {
        return session.prepare(
            select(CassandraACLTable.ACL, CassandraACLTable.VERSION)
                .from(CassandraACLTable.TABLE_NAME)
                .where(eq(CassandraMailboxTable.ID, bindMarker(CassandraACLTable.ID))));
    }

    public CompletableFuture<MailboxACL> getACL(CassandraId cassandraId) {
        return getStoredACLRow(cassandraId)
            .thenApply(resultSet -> getAcl(cassandraId, resultSet));
    }

    private MailboxACL getAcl(CassandraId cassandraId, ResultSet resultSet) {
        if (resultSet.isExhausted()) {
            return MailboxACL.EMPTY;
        }
        String serializedACL = resultSet.one().getString(CassandraACLTable.ACL);
        return deserializeACL(cassandraId, serializedACL);
    }

    public void updateACL(CassandraId cassandraId, MailboxACL.ACLCommand command) throws MailboxException {
        MailboxACL replacement = MailboxACL.EMPTY.apply(command);

        updateAcl(cassandraId, aclWithVersion -> aclWithVersion.apply(command), replacement);
    }

    public void setACL(CassandraId cassandraId, MailboxACL mailboxACL) throws MailboxException {
        updateAcl(cassandraId,
            acl -> new ACLWithVersion(acl.version, mailboxACL),
            mailboxACL);
    }

    private void updateAcl(CassandraId cassandraId, Function<ACLWithVersion, ACLWithVersion> aclTransformation, MailboxACL replacement) throws MailboxException {
        try {
            new FunctionRunnerWithRetry(maxRetry)
                .execute(
                    () -> {
                        codeInjector.inject();
                        return getAclWithVersion(cassandraId)
                            .map(aclTransformation)
                            .map(aclWithVersion -> updateStoredACL(cassandraId, aclWithVersion))
                            .orElseGet(() -> insertACL(cassandraId, replacement));
                    });
        } catch (LightweightTransactionException e) {
            throw new MailboxException("Exception during lightweight transaction", e);
        }
    }

    private CompletableFuture<ResultSet> getStoredACLRow(CassandraId cassandraId) {
        return executor.execute(
            readStatement.bind()
                .setUUID(CassandraACLTable.ID, cassandraId.asUuid()));
    }

    private boolean updateStoredACL(CassandraId cassandraId, ACLWithVersion aclWithVersion) {
        try {
            return executor.executeReturnApplied(
                conditionalUpdateStatement.bind()
                    .setUUID(CassandraACLTable.ID, cassandraId.asUuid())
                    .setString(CassandraACLTable.ACL,  MailboxACLJsonConverter.toJson(aclWithVersion.mailboxACL))
                    .setLong(CassandraACLTable.VERSION, aclWithVersion.version + 1)
                    .setLong(OLD_VERSION, aclWithVersion.version))
                .join();
        } catch (JsonProcessingException exception) {
            throw Throwables.propagate(exception);
        }
    }

    private boolean insertACL(CassandraId cassandraId, MailboxACL acl) {
        try {
            return executor.executeReturnApplied(
                conditionalInsertStatement.bind()
                    .setUUID(CassandraACLTable.ID, cassandraId.asUuid())
                    .setString(CassandraACLTable.ACL, MailboxACLJsonConverter.toJson(acl)))
                .join();
        } catch (JsonProcessingException exception) {
            throw Throwables.propagate(exception);
        }
    }

    private Optional<ACLWithVersion> getAclWithVersion(CassandraId cassandraId) {
        ResultSet resultSet = getStoredACLRow(cassandraId).join();
        if (resultSet.isExhausted()) {
            return Optional.empty();
        }
        Row row = resultSet.one();
        return Optional.of(
            new ACLWithVersion(
                row.getLong(CassandraACLTable.VERSION),
                deserializeACL(cassandraId, row.getString(CassandraACLTable.ACL))));
    }

    private MailboxACL deserializeACL(CassandraId cassandraId, String serializedACL) {
        try {
            return MailboxACLJsonConverter.toACL(serializedACL);
        } catch(IOException exception) {
            LOG.error("Unable to read stored ACL. " +
                "We will use empty ACL instead." +
                "Mailbox is {} ." +
                "ACL is {}", cassandraId, serializedACL, exception);
            return MailboxACL.EMPTY;
        }
    }

    private class ACLWithVersion {
        private final long version;
        private final MailboxACL mailboxACL;

        public ACLWithVersion(long version, MailboxACL mailboxACL) {
            this.version = version;
            this.mailboxACL = mailboxACL;
        }

        public ACLWithVersion apply(MailboxACL.ACLCommand command) {
            try {
                return new ACLWithVersion(version, mailboxACL.apply(command));
            } catch(UnsupportedRightException exception) {
                throw Throwables.propagate(exception);
            }
        }
    }
}
