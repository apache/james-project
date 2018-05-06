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
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentOwnerTable.FIELDS;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentOwnerTable.ID;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentOwnerTable.OWNER;
import static org.apache.james.mailbox.cassandra.table.CassandraAttachmentOwnerTable.TABLE_NAME;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.store.mail.model.Username;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;

public class CassandraAttachmentOwnerDAO {

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement addStatement;
    private final CassandraUtils cassandraUtils;
    private final PreparedStatement selectStatement;

    @Inject
    public CassandraAttachmentOwnerDAO(Session session, CassandraUtils cassandraUtils) {
        this.executor = new CassandraAsyncExecutor(session);

        this.selectStatement = prepareSelect(session);
        this.addStatement = prepareAdd(session);
        this.cassandraUtils = cassandraUtils;
    }

    private PreparedStatement prepareAdd(Session session) {
        return session.prepare(
            insertInto(TABLE_NAME)
                .value(ID, bindMarker(ID))
                .value(OWNER, bindMarker(OWNER)));
    }

    private PreparedStatement prepareSelect(Session session) {
        return session.prepare(
            select(FIELDS)
                .from(TABLE_NAME)
                .where(eq(ID, bindMarker(ID))));
    }

    public CompletableFuture<Void> addOwner(AttachmentId attachmentId, Username owner) {
        return executor.executeVoid(
            addStatement.bind()
                .setUUID(ID, attachmentId.asUUID())
                .setString(OWNER, owner.getValue()));
    }

    public CompletableFuture<Stream<Username>> retrieveOwners(AttachmentId attachmentId) {
        return executor.execute(
            selectStatement.bind()
                .setUUID(ID, attachmentId.asUUID()))
            .thenApply(cassandraUtils::convertToStream)
            .thenApply(this::toOwners);
    }

    private Stream<Username> toOwners(Stream<Row> stream) {
        return stream.map(row -> row.getString(OWNER))
            .map(Username::fromRawValue);
    }
}
