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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.init.CassandraConfiguration;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.utils.MailboxBaseTupleUtil;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxTable;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.apache.james.util.CompletableFutureUtil;
import org.apache.james.util.FluentFutureStream;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.annotations.VisibleForTesting;

public class CassandraMailboxDAO {

    private final CassandraAsyncExecutor executor;
    private final MailboxBaseTupleUtil mailboxBaseTupleUtil;
    private final CassandraUtils cassandraUtils;
    private final PreparedStatement readStatement;
    private final PreparedStatement listStatement;
    private final PreparedStatement deleteStatement;
    private final PreparedStatement insertStatement;
    private final PreparedStatement updateStatement;
    private CassandraACLMapper cassandraACLMapper;

    @Inject
    public CassandraMailboxDAO(Session session, CassandraTypesProvider typesProvider, CassandraUtils cassandraUtils, CassandraConfiguration cassandraConfiguration) {
        this.executor = new CassandraAsyncExecutor(session);
        this.mailboxBaseTupleUtil = new MailboxBaseTupleUtil(typesProvider);
        this.insertStatement = prepareInsert(session);
        this.updateStatement = prepareUpdate(session);
        this.deleteStatement = prepareDelete(session);
        this.listStatement = prepareList(session);
        this.readStatement = prepareRead(session);
        this.cassandraUtils = cassandraUtils;
        this.cassandraACLMapper = new CassandraACLMapper(session, cassandraConfiguration);
    }

    @VisibleForTesting
    public CassandraMailboxDAO(Session session, CassandraTypesProvider typesProvider) {
        this(session, typesProvider, CassandraUtils.WITH_DEFAULT_CONFIGURATION, CassandraConfiguration.DEFAULT_CONFIGURATION);
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

    public CompletableFuture<Void> save(Mailbox mailbox) {
        CassandraId cassandraId = (CassandraId) mailbox.getMailboxId();
        return executor.executeVoid(insertStatement.bind()
            .setUUID(ID, cassandraId.asUuid())
            .setString(NAME, mailbox.getName())
            .setLong(UIDVALIDITY, mailbox.getUidValidity())
            .setUDTValue(MAILBOX_BASE, mailboxBaseTupleUtil.createMailboxBaseUDT(mailbox.getNamespace(), mailbox.getUser())));
    }

    public CompletableFuture<Void> updatePath(CassandraId mailboxId, MailboxPath mailboxPath) {
        return executor.executeVoid(updateStatement.bind()
            .setUUID(ID, mailboxId.asUuid())
            .setString(NAME, mailboxPath.getName())
            .setUDTValue(MAILBOX_BASE, mailboxBaseTupleUtil.createMailboxBaseUDT(mailboxPath.getNamespace(), mailboxPath.getUser())));
    }

    public CompletableFuture<Void> delete(CassandraId mailboxId) {
        return executor.executeVoid(deleteStatement.bind()
            .setUUID(ID, mailboxId.asUuid()));
    }

    public CompletableFuture<Optional<SimpleMailbox>> retrieveMailbox(CassandraId mailboxId) {
        CompletableFuture<MailboxACL> aclCompletableFuture = cassandraACLMapper.getACL(mailboxId);

        CompletableFuture<Optional<SimpleMailbox>> simpleMailboxFuture = executor.executeSingleRow(readStatement.bind()
            .setUUID(ID, mailboxId.asUuid()))
            .thenApply(rowOptional -> rowOptional.map(this::mailboxFromRow))
            .thenApply(mailbox -> addMailboxId(mailboxId, mailbox));

        return CompletableFutureUtil.combine(
            aclCompletableFuture,
            simpleMailboxFuture,
            this::addAcl);
    }

    private Optional<SimpleMailbox> addMailboxId(CassandraId cassandraId, Optional<SimpleMailbox> mailboxOptional) {
        mailboxOptional.ifPresent(mailbox -> mailbox.setMailboxId(cassandraId));
        return mailboxOptional;
    }

    private Optional<SimpleMailbox> addAcl(MailboxACL acl, Optional<SimpleMailbox> mailboxOptional) {
        mailboxOptional.ifPresent(mailbox -> mailbox.setACL(acl));
        return mailboxOptional;
    }

    private SimpleMailbox mailboxFromRow(Row row) {
        return new SimpleMailbox(
            new MailboxPath(
                row.getUDTValue(MAILBOX_BASE).getString(CassandraMailboxTable.MailboxBase.NAMESPACE),
                row.getUDTValue(MAILBOX_BASE).getString(CassandraMailboxTable.MailboxBase.USER),
                row.getString(NAME)),
            row.getLong(UIDVALIDITY));
    }

    public CompletableFuture<Stream<SimpleMailbox>> retrieveAllMailboxes() {
        return FluentFutureStream.of(executor.execute(listStatement.bind())
            .thenApply(cassandraUtils::convertToStream))
            .map(this::toMailboxWithId)
            .thenComposeOnAll(this::toMailboxWithAclFuture)
            .completableFuture();
    }

    private SimpleMailbox toMailboxWithId(Row row) {
        SimpleMailbox mailbox = mailboxFromRow(row);
        mailbox.setMailboxId(CassandraId.of(row.getUUID(ID)));
        return mailbox;
    }

    private CompletableFuture<SimpleMailbox> toMailboxWithAclFuture(SimpleMailbox mailbox) {
        CassandraId cassandraId = (CassandraId) mailbox.getMailboxId();
        return cassandraACLMapper.getACL(cassandraId)
            .thenApply(acl -> {
                mailbox.setACL(acl);
                return mailbox;
            });
    }

}
