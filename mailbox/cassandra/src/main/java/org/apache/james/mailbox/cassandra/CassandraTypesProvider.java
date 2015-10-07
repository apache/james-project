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

package org.apache.james.mailbox.cassandra;

import com.datastax.driver.core.Session;
import com.datastax.driver.core.UserType;
import com.datastax.driver.core.schemabuilder.CreateType;
import com.datastax.driver.core.schemabuilder.SchemaBuilder;
import com.google.common.collect.ImmutableMap;
import org.apache.james.mailbox.cassandra.table.CassandraMailboxTable;
import org.apache.james.mailbox.cassandra.table.CassandraMessageTable;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.datastax.driver.core.DataType.text;

public class CassandraTypesProvider {

    public enum TYPE {
        MailboxBase(CassandraMailboxTable.MAILBOX_BASE,
            SchemaBuilder.createType(CassandraMailboxTable.MAILBOX_BASE)
                .ifNotExists()
                .addColumn(CassandraMailboxTable.MailboxBase.NAMESPACE, text())
                .addColumn(CassandraMailboxTable.MailboxBase.USER, text())),
        Property(CassandraMessageTable.PROPERTIES,
            SchemaBuilder.createType(CassandraMessageTable.PROPERTIES)
                .ifNotExists()
                .addColumn(CassandraMessageTable.Properties.NAMESPACE, text())
                .addColumn(CassandraMessageTable.Properties.NAME, text())
                .addColumn(CassandraMessageTable.Properties.VALUE, text()))
        ;

        private final String name;
        private final CreateType createStatement;

        TYPE(String name, CreateType createStatement) {
            this.name = name;
            this.createStatement = createStatement;
        }

        public String getName() {
            return name;
        }
    }

    private final ImmutableMap<TYPE, UserType> userTypes;
    private final Session session;

    public CassandraTypesProvider(Session session) {
        this.session = session;
        initializeTypes();
        userTypes = ImmutableMap.<TYPE, UserType>builder()
            .putAll(Arrays.stream(TYPE.values())
                .collect(Collectors.toMap(
                    (type) -> type,
                    (type) -> session.getCluster()
                        .getMetadata()
                        .getKeyspace(session.getLoggedKeyspace())
                        .getUserType(type.name)))).build();
    }

    public UserType getDefinedUserType(TYPE type) {
        return Optional.ofNullable(userTypes.get(type))
            .orElseThrow(() -> new RuntimeException("Cassandra UDT " + type.getName() + " can not be retrieved"));
    }

    public void initializeTypes() {
        Arrays.asList(TYPE.values())
            .forEach((type) -> session.execute(type.createStatement));
    }

}
