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
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.update;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static com.datastax.oss.driver.api.querybuilder.update.Assignment.setColumn;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.FIELDS;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.ID;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.MAILBOX_BASE;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.TABLE_NAME;
import static org.apache.james.mailbox.cassandra.table.CassandraMailboxTable.UIDVALIDITY;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.utils.MailboxBaseTupleUtil;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxTable;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.data.UdtValue;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraMailboxDAO {
    private final CassandraAsyncExecutor executor;
    private final MailboxBaseTupleUtil mailboxBaseTupleUtil;
    private final PreparedStatement readStatement;
    private final PreparedStatement listStatement;
    private final PreparedStatement deleteStatement;
    private final PreparedStatement insertStatement;
    private final PreparedStatement updateStatement;
    private final PreparedStatement updateUidValidityStatement;
    private final CqlSession session;
    private final TypeCodec<UdtValue> mailboxBaseTypeCodec;

    @Inject
    public CassandraMailboxDAO(CqlSession session, CassandraTypesProvider typesProvider) {
        this.session = session;
        this.executor = new CassandraAsyncExecutor(session);
        this.mailboxBaseTupleUtil = new MailboxBaseTupleUtil(typesProvider);
        this.insertStatement = prepareInsert();
        this.updateStatement = prepareUpdate();
        this.updateUidValidityStatement = prepareUpdateUidValidity();
        this.deleteStatement = prepareDelete();
        this.listStatement = prepareList();
        this.readStatement = prepareRead();

        this.mailboxBaseTypeCodec = CodecRegistry.DEFAULT.codecFor(typesProvider.getDefinedUserType(MAILBOX_BASE.asCql(true)));
    }

    private PreparedStatement prepareInsert() {
        return session.prepare(insertInto(TABLE_NAME)
            .value(ID, bindMarker(ID))
            .value(NAME, bindMarker(NAME))
            .value(UIDVALIDITY, bindMarker(UIDVALIDITY))
            .value(MAILBOX_BASE, bindMarker(MAILBOX_BASE))
            .build());
    }

    private PreparedStatement prepareUpdate() {
        return session.prepare(update(TABLE_NAME)
            .set(setColumn(MAILBOX_BASE, bindMarker(MAILBOX_BASE)),
                setColumn(NAME, bindMarker(NAME)))
            .where(column(ID).isEqualTo(bindMarker(ID)))
            .build());
    }

    private PreparedStatement prepareUpdateUidValidity() {
        return session.prepare(update(TABLE_NAME)
            .setColumn(UIDVALIDITY, bindMarker(UIDVALIDITY))
            .where(column(ID).isEqualTo(bindMarker(ID)))
            .build());
    }

    private PreparedStatement prepareDelete() {
        return session.prepare(deleteFrom(TABLE_NAME)
            .where(column(ID).isEqualTo(bindMarker(ID)))
            .build());
    }

    private PreparedStatement prepareList() {
        return session.prepare(selectFrom(TABLE_NAME)
            .columns(FIELDS)
            .build());
    }

    private PreparedStatement prepareRead() {
        return session.prepare(selectFrom(TABLE_NAME)
            .columns(FIELDS)
            .where(column(ID).isEqualTo(bindMarker(ID)))
            .build());
    }

    public Mono<Void> save(Mailbox mailbox) {
        CassandraId cassandraId = (CassandraId) mailbox.getMailboxId();
        return executor.executeVoid(insertStatement.bind()
            .setUuid(ID, cassandraId.asUuid())
            .setString(NAME, mailbox.getName())
            .setLong(UIDVALIDITY, mailbox.getUidValidity().asLong())
            .setUdtValue(MAILBOX_BASE, mailboxBaseTupleUtil.createMailboxBaseUDT(mailbox.getNamespace(), mailbox.getUser())));
    }

    public Mono<Void> updatePath(CassandraId mailboxId, MailboxPath mailboxPath) {
        return executor.executeVoid(updateStatement.bind()
            .setUuid(ID, mailboxId.asUuid())
            .setString(NAME, mailboxPath.getName())
            .setUdtValue(MAILBOX_BASE, mailboxBaseTupleUtil.createMailboxBaseUDT(mailboxPath.getNamespace(), mailboxPath.getUser())));
    }

    public Mono<Void> delete(CassandraId mailboxId) {
        return executor.executeVoid(deleteStatement.bind()
            .setUuid(ID, mailboxId.asUuid()));
    }

    public Mono<Mailbox> retrieveMailbox(CassandraId mailboxId) {
        return executor.executeSingleRow(readStatement.bind()
                .set(ID, mailboxId.asUuid(), TypeCodecs.TIMEUUID))
            .flatMap(row -> mailboxFromRow(row, mailboxId));
    }

    private Mono<Mailbox> mailboxFromRow(Row row, CassandraId cassandraId) {
        return sanitizeUidValidity(cassandraId, row.getLong(UIDVALIDITY))
            .map(uidValidity -> {
                UdtValue mailboxBase = row.get(MAILBOX_BASE, mailboxBaseTypeCodec);
                return new Mailbox(
                    new MailboxPath(
                        mailboxBase.get(CassandraMailboxTable.MailboxBase.NAMESPACE, TypeCodecs.TEXT),
                        Username.of(mailboxBase.get(CassandraMailboxTable.MailboxBase.USER, TypeCodecs.TEXT)),
                        row.get(NAME, TypeCodecs.TEXT)),
                    uidValidity,
                    cassandraId);
            });
    }

    private Mono<UidValidity> sanitizeUidValidity(CassandraId cassandraId, long uidValidityAsLong) {
        if (!UidValidity.isValid(uidValidityAsLong)) {
            UidValidity newUidValidity = UidValidity.generate();
            return updateUidValidity(cassandraId, newUidValidity)
                .then(Mono.just(newUidValidity));
        }
        return Mono.just(UidValidity.of(uidValidityAsLong));
    }

    /**
     * Expected concurrency issue in the absence of performance expensive LightWeight transaction
     * As the Uid validity is updated only when equal to 0 (1 chance out of 4 billion) the benefits of LWT don't
     * outweigh the performance costs
     */
    private Mono<Void> updateUidValidity(CassandraId cassandraId, UidValidity uidValidity) {
        return executor.executeVoid(updateUidValidityStatement.bind()
            .setUuid(ID, cassandraId.asUuid())
            .setLong(UIDVALIDITY, uidValidity.asLong()));
    }

    public Flux<Mailbox> retrieveAllMailboxes() {
        return executor.executeRows(listStatement.bind())
            .flatMap(this::toMailboxWithId, DEFAULT_CONCURRENCY);
    }

    private Mono<Mailbox> toMailboxWithId(Row row) {
        return mailboxFromRow(row, CassandraId.of(row.getUuid(ID)));
    }
}
