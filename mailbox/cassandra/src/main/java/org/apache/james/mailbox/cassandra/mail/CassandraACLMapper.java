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

import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.set;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;

import java.io.IOException;
import java.util.Optional;

import org.apache.james.mailbox.cassandra.CassandraConstants;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.cassandra.mail.utils.FunctionRunnerWithRetry;
import org.apache.james.mailbox.cassandra.mail.utils.SimpleMailboxACLJsonConverter;
import org.apache.james.mailbox.cassandra.table.CassandraACLTable;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxTable;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.SimpleMailboxACL;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

public class CassandraACLMapper {

    @FunctionalInterface
    public interface CodeInjector {
        void inject();
    }

    private final Mailbox<CassandraId> mailbox;
    private final Session session;
    private final int maxRetry;
    private final CodeInjector codeInjector;

    private static final Logger LOG = LoggerFactory.getLogger(CassandraACLMapper.class);

    public CassandraACLMapper(Mailbox<CassandraId> mailbox, Session session, int maxRetry) {
        this(mailbox, session, maxRetry, () -> {});
    }

    public CassandraACLMapper(Mailbox<CassandraId> mailbox, Session session, int maxRetry, CodeInjector codeInjector) {
        Preconditions.checkArgument(maxRetry > 0);
        Preconditions.checkArgument(mailbox.getMailboxId() != null);
        this.mailbox = mailbox;
        this.session = session;
        this.maxRetry = maxRetry;
        this.codeInjector = codeInjector;
    }

    public MailboxACL getACL() {
        ResultSet resultSet = getStoredACLRow();
        if (resultSet.isExhausted()) {
            return SimpleMailboxACL.EMPTY;
        }
        String serializedACL = resultSet.one().getString(CassandraACLTable.ACL);
        return deserializeACL(serializedACL);
    }

    public void updateACL(final MailboxACL.MailboxACLCommand command) throws MailboxException {
        new FunctionRunnerWithRetry(maxRetry).execute(
            () -> {
                codeInjector.inject();
                ResultSet resultSet = getAclWithVersion()
                        .map((x) -> x.apply(command))
                        .map(this::updateStoredACL)
                        .orElseGet(() -> insertACL(applyCommandOnEmptyACL(command)));
                return resultSet.one().getBool(CassandraConstants.LIGHTWEIGHT_TRANSACTION_APPLIED);
            }
        );
    }

    private MailboxACL applyCommandOnEmptyACL(MailboxACL.MailboxACLCommand command) {
        try {
            return SimpleMailboxACL.EMPTY.apply(command);
        } catch (UnsupportedRightException exception) {
            throw Throwables.propagate(exception);
        }
    }

    private ResultSet getStoredACLRow() {
        return session.execute(
            select(CassandraACLTable.ACL, CassandraACLTable.VERSION)
                .from(CassandraACLTable.TABLE_NAME)
                .where(eq(CassandraMailboxTable.ID, mailbox.getMailboxId().asUuid()))
        );
    }

    private ResultSet updateStoredACL(ACLWithVersion aclWithVersion) {
        try {
            return session.execute(
                update(CassandraACLTable.TABLE_NAME)
                    .with(set(CassandraACLTable.ACL, SimpleMailboxACLJsonConverter.toJson(aclWithVersion.mailboxACL)))
                    .and(set(CassandraACLTable.VERSION, aclWithVersion.version + 1))
                    .where(eq(CassandraACLTable.ID, mailbox.getMailboxId().asUuid()))
                    .onlyIf(eq(CassandraACLTable.VERSION, aclWithVersion.version))
            );
        } catch (JsonProcessingException exception) {
            throw Throwables.propagate(exception);
        }
    }

    private ResultSet insertACL(MailboxACL acl) {
        try {
            return session.execute(
                insertInto(CassandraACLTable.TABLE_NAME)
                    .value(CassandraACLTable.ID, mailbox.getMailboxId().asUuid())
                    .value(CassandraACLTable.ACL, SimpleMailboxACLJsonConverter.toJson(acl))
                    .value(CassandraACLTable.VERSION, 0)
                    .ifNotExists()
            );
        } catch (JsonProcessingException exception) {
            throw Throwables.propagate(exception);
        }
    }

    private Optional<ACLWithVersion> getAclWithVersion() {
        ResultSet resultSet = getStoredACLRow();
        if (resultSet.isExhausted()) {
            return Optional.empty();
        }
        Row row = resultSet.one();
        return Optional.of(new ACLWithVersion(row.getLong(CassandraACLTable.VERSION), deserializeACL(row.getString(CassandraACLTable.ACL))));
    }

    private MailboxACL deserializeACL(String serializedACL) {
        try {
            return SimpleMailboxACLJsonConverter.toACL(serializedACL);
        } catch(IOException exception) {
            LOG.error("Unable to read stored ACL. " +
                "We will use empty ACL instead." +
                "Mailbox is {}:{}:{} ." +
                "ACL is {}", mailbox.getNamespace(), mailbox.getUser(), mailbox.getName(), serializedACL, exception);
            return SimpleMailboxACL.EMPTY;
        }
    }

    private class ACLWithVersion {
        private final long version;
        private final MailboxACL mailboxACL;

        public ACLWithVersion(long version, MailboxACL mailboxACL) {
            this.version = version;
            this.mailboxACL = mailboxACL;
        }

        public ACLWithVersion apply(MailboxACL.MailboxACLCommand command) {
            try {
                return new ACLWithVersion(version, mailboxACL.apply(command));
            } catch(UnsupportedRightException exception) {
                throw Throwables.propagate(exception);
            }
        }
    }
}
