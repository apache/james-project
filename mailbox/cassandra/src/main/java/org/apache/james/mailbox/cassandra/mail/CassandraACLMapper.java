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
import java.util.function.Function;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.table.CassandraACLTable;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxTable;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.store.json.MailboxACLJsonConverter;
import org.apache.james.util.FunctionalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.fasterxml.jackson.core.JsonProcessingException;

import reactor.core.publisher.Mono;

public class CassandraACLMapper {
    public static final int INITIAL_VALUE = 0;
    private static final Logger LOG = LoggerFactory.getLogger(CassandraACLMapper.class);
    private static final String OLD_VERSION = "oldVersion";

    private final CassandraAsyncExecutor executor;
    private final int maxAclRetry;
    private final CassandraUserMailboxRightsDAO userMailboxRightsDAO;
    private final PreparedStatement conditionalInsertStatement;
    private final PreparedStatement conditionalUpdateStatement;
    private final PreparedStatement readStatement;

    @Inject
    public CassandraACLMapper(Session session, CassandraUserMailboxRightsDAO userMailboxRightsDAO, CassandraConfiguration cassandraConfiguration) {
        this.executor = new CassandraAsyncExecutor(session);
        this.maxAclRetry = cassandraConfiguration.getAclMaxRetry();
        this.conditionalInsertStatement = prepareConditionalInsert(session);
        this.conditionalUpdateStatement = prepareConditionalUpdate(session);
        this.readStatement = prepareReadStatement(session);
        this.userMailboxRightsDAO = userMailboxRightsDAO;
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

    public Mono<MailboxACL> getACL(CassandraId cassandraId) {
        return getStoredACLRow(cassandraId)
            .map(row -> getAcl(cassandraId, row))
            .switchIfEmpty(Mono.just(MailboxACL.EMPTY));
    }

    private MailboxACL getAcl(CassandraId cassandraId, Row row) {
        String serializedACL = row.getString(CassandraACLTable.ACL);
        return deserializeACL(cassandraId, serializedACL);
    }

    public ACLDiff updateACL(CassandraId cassandraId, MailboxACL.ACLCommand command) throws MailboxException {
        MailboxACL replacement = MailboxACL.EMPTY.apply(command);
        return updateAcl(cassandraId, aclWithVersion -> aclWithVersion.apply(command), replacement)
            .flatMap(aclDiff -> userMailboxRightsDAO.update(cassandraId, aclDiff)
            .thenReturn(aclDiff))
            .blockOptional()
            .orElseThrow(() -> new MailboxException("Unable to update ACL"));
    }

    public ACLDiff setACL(CassandraId cassandraId, MailboxACL mailboxACL) throws MailboxException {
        return updateAcl(cassandraId, acl -> new ACLWithVersion(acl.version, mailboxACL), mailboxACL)
            .flatMap(aclDiff -> userMailboxRightsDAO.update(cassandraId, aclDiff)
            .thenReturn(aclDiff))
            .blockOptional()
            .orElseThrow(() -> new MailboxException("Unable to update ACL"));
    }

    private Mono<ACLDiff> updateAcl(CassandraId cassandraId, Function<ACLWithVersion, ACLWithVersion> aclTransformation, MailboxACL replacement) {
        return getAclWithVersion(cassandraId)
            .flatMap(aclWithVersion ->
                    updateStoredACL(cassandraId, aclTransformation.apply(aclWithVersion))
                            .map(newACL -> ACLDiff.computeDiff(aclWithVersion.mailboxACL, newACL)))
            .switchIfEmpty(insertACL(cassandraId, replacement)
                    .map(newACL -> ACLDiff.computeDiff(MailboxACL.EMPTY, newACL)))
            .single()
            .retry(maxAclRetry);
    }

    private Mono<Row> getStoredACLRow(CassandraId cassandraId) {
        return executor.executeSingleRow(
            readStatement.bind()
                .setUUID(CassandraACLTable.ID, cassandraId.asUuid())
                .setConsistencyLevel(ConsistencyLevel.SERIAL));
    }

    private Mono<MailboxACL> updateStoredACL(CassandraId cassandraId, ACLWithVersion aclWithVersion) {
        return executor.executeReturnApplied(
            conditionalUpdateStatement.bind()
                .setUUID(CassandraACLTable.ID, cassandraId.asUuid())
                .setString(CassandraACLTable.ACL, convertAclToJson(aclWithVersion.mailboxACL))
                .setLong(CassandraACLTable.VERSION, aclWithVersion.version + 1)
                .setLong(OLD_VERSION, aclWithVersion.version))
            .filter(FunctionalUtils.identityPredicate())
            .map(any -> aclWithVersion.mailboxACL);
    }

    private Mono<MailboxACL> insertACL(CassandraId cassandraId, MailboxACL acl) {
        return Mono.defer(() -> executor.executeReturnApplied(
            conditionalInsertStatement.bind()
                    .setUUID(CassandraACLTable.ID, cassandraId.asUuid())
                    .setString(CassandraACLTable.ACL, convertAclToJson(acl))))
            .filter(FunctionalUtils.identityPredicate())
            .map(any -> acl);
    }

    private String convertAclToJson(MailboxACL acl) {
        try {
            return MailboxACLJsonConverter.toJson(acl);
        } catch (JsonProcessingException exception) {
            throw new RuntimeException(exception);
        }
    }

    private Mono<ACLWithVersion> getAclWithVersion(CassandraId cassandraId) {
        return getStoredACLRow(cassandraId)
            .map(acl -> new ACLWithVersion(acl.getLong(CassandraACLTable.VERSION),
                deserializeACL(cassandraId, acl.getString(CassandraACLTable.ACL))));
    }

    private MailboxACL deserializeACL(CassandraId cassandraId, String serializedACL) {
        try {
            return MailboxACLJsonConverter.toACL(serializedACL);
        } catch (IOException exception) {
            LOG.error("Unable to read stored ACL. " +
                "We will use empty ACL instead." +
                "Mailbox is {} ." +
                "ACL is {}", cassandraId, serializedACL, exception);
            return MailboxACL.EMPTY;
        }
    }

    private static class ACLWithVersion {
        private final long version;
        private final MailboxACL mailboxACL;

        public ACLWithVersion(long version, MailboxACL mailboxACL) {
            this.version = version;
            this.mailboxACL = mailboxACL;
        }

        public ACLWithVersion apply(MailboxACL.ACLCommand command) {
            try {
                return new ACLWithVersion(version, mailboxACL.apply(command));
            } catch (UnsupportedRightException exception) {
                throw new RuntimeException(exception);
            }
        }
    }
}
