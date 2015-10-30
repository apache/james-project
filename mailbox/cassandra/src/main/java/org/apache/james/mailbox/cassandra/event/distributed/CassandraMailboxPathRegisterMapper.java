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

package org.apache.james.mailbox.cassandra.event.distributed;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.ttl;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.UDTValue;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxPathRegisterTable;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.event.distributed.DistantMailboxPathRegisterMapper;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class CassandraMailboxPathRegisterMapper implements DistantMailboxPathRegisterMapper {

    private final Session session;
    private final CassandraTypesProvider typesProvider;
    private final PreparedStatement insertStatement;
    private final PreparedStatement deleteStatement;
    private final PreparedStatement selectStatement;

    private int cassandraTimeOutInS;

    public CassandraMailboxPathRegisterMapper(Session session, CassandraTypesProvider typesProvider) {
        this.session = session;
        this.typesProvider = typesProvider;
        this.insertStatement = session.prepare(insertInto(CassandraMailboxPathRegisterTable.TABLE_NAME)
            .value(CassandraMailboxPathRegisterTable.MAILBOX_PATH, bindMarker())
            .value(CassandraMailboxPathRegisterTable.TOPIC, bindMarker())
            .using(ttl(bindMarker())));
        this.deleteStatement = session.prepare(delete().from(CassandraMailboxPathRegisterTable.TABLE_NAME)
            .where(eq(CassandraMailboxPathRegisterTable.MAILBOX_PATH, bindMarker()))
            .and(eq(CassandraMailboxPathRegisterTable.TOPIC, bindMarker())));
        this.selectStatement = session.prepare(select().from(CassandraMailboxPathRegisterTable.TABLE_NAME)
            .where(eq(CassandraMailboxPathRegisterTable.MAILBOX_PATH, bindMarker())));
    }

    public void setCassandraTimeOutInS(int cassandraTimeOutInS) {
        this.cassandraTimeOutInS = cassandraTimeOutInS;
    }

    @Override
    public Set<String> getTopics(MailboxPath mailboxPath) {
        return convertToStream(session.execute(selectStatement.bind(buildUDTFromMailboxPath(mailboxPath))))
            .map(row -> row.getString(CassandraMailboxPathRegisterTable.TOPIC))
            .collect(Collectors.toSet());
    }

    @Override
    public void doRegister(MailboxPath mailboxPath, String topic) {
        session.execute(insertStatement.bind(buildUDTFromMailboxPath(mailboxPath), topic, cassandraTimeOutInS));
    }

    @Override
    public void doUnRegister(MailboxPath mailboxPath, String topic) {
        session.execute(deleteStatement.bind(buildUDTFromMailboxPath(mailboxPath), topic));
    }

    private UDTValue buildUDTFromMailboxPath(MailboxPath path) {
        return typesProvider.getDefinedUserType(CassandraMailboxPathRegisterTable.MAILBOX_PATH)
            .newValue()
            .setString(CassandraMailboxPathRegisterTable.MailboxPath.NAMESPACE, path.getNamespace())
            .setString(CassandraMailboxPathRegisterTable.MailboxPath.USER, path.getUser())
            .setString(CassandraMailboxPathRegisterTable.MailboxPath.NAME, path.getName());
    }

    private Stream<Row> convertToStream(ResultSet resultSet) {
        return StreamSupport.stream(resultSet.spliterator(), true);
    }

}
