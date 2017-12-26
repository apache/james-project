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

import java.util.Set;
import java.util.stream.Collectors;

import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxPathRegisterTable;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.event.distributed.DistantMailboxPathRegisterMapper;
import org.apache.james.mailbox.store.publisher.Topic;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.UDTValue;

public class CassandraMailboxPathRegisterMapper implements DistantMailboxPathRegisterMapper {

    private final Session session;
    private final CassandraTypesProvider typesProvider;
    private final int cassandraTimeOutInS;
    private final CassandraUtils cassandraUtils;
    private final PreparedStatement insertStatement;
    private final PreparedStatement deleteStatement;
    private final PreparedStatement selectStatement;

    public CassandraMailboxPathRegisterMapper(Session session, CassandraTypesProvider typesProvider, CassandraUtils cassandraUtils, int cassandraTimeOutInS) {
        this.session = session;
        this.typesProvider = typesProvider;
        this.cassandraTimeOutInS = cassandraTimeOutInS;
        this.insertStatement = session.prepare(insertInto(CassandraMailboxPathRegisterTable.TABLE_NAME)
            .value(CassandraMailboxPathRegisterTable.MAILBOX_PATH, bindMarker())
            .value(CassandraMailboxPathRegisterTable.TOPIC, bindMarker())
            .using(ttl(bindMarker())));
        this.deleteStatement = session.prepare(delete().from(CassandraMailboxPathRegisterTable.TABLE_NAME)
            .where(eq(CassandraMailboxPathRegisterTable.MAILBOX_PATH, bindMarker()))
            .and(eq(CassandraMailboxPathRegisterTable.TOPIC, bindMarker())));
        this.selectStatement = session.prepare(select().from(CassandraMailboxPathRegisterTable.TABLE_NAME)
            .where(eq(CassandraMailboxPathRegisterTable.MAILBOX_PATH, bindMarker())));
        this.cassandraUtils = cassandraUtils;
    }

    @Override
    public Set<Topic> getTopics(MailboxPath mailboxPath) {
        return cassandraUtils.convertToStream(session.execute(selectStatement.bind(buildUDTFromMailboxPath(mailboxPath))))
            .map(row -> new Topic(row.getString(CassandraMailboxPathRegisterTable.TOPIC)))
            .collect(Collectors.toSet());
    }

    @Override
    public void doRegister(MailboxPath mailboxPath, Topic topic) {
        session.execute(insertStatement.bind(buildUDTFromMailboxPath(mailboxPath), topic.getValue(), cassandraTimeOutInS));
    }

    @Override
    public void doUnRegister(MailboxPath mailboxPath, Topic topic) {
        session.execute(deleteStatement.bind(buildUDTFromMailboxPath(mailboxPath), topic.getValue()));
    }

    private UDTValue buildUDTFromMailboxPath(MailboxPath path) {
        return typesProvider.getDefinedUserType(CassandraMailboxPathRegisterTable.MAILBOX_PATH)
            .newValue()
            .setString(CassandraMailboxPathRegisterTable.MailboxPath.NAMESPACE, path.getNamespace())
            .setString(CassandraMailboxPathRegisterTable.MailboxPath.USER, path.getUser())
            .setString(CassandraMailboxPathRegisterTable.MailboxPath.NAME, path.getName());
    }

}
