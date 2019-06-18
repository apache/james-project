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

package org.apache.james.backends.cassandra.init;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.components.CassandraType;
import org.apache.james.backends.cassandra.components.CassandraType.InitializationStatus;

import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.Session;
import com.google.common.collect.ImmutableList;

public class CassandraTypesCreator {
    private final ImmutableList<CassandraType> types;
    private final Session session;

    public CassandraTypesCreator(CassandraModule module, Session session) {
        this.types = ImmutableList.copyOf(module.moduleTypes());
        this.session = session;
    }

    public InitializationStatus initializeTypes() {
        KeyspaceMetadata keyspaceMetadata = session.getCluster()
                .getMetadata()
                .getKeyspace(session.getLoggedKeyspace());

        return types.stream()
                .map(type -> type.initialize(keyspaceMetadata, session))
                .reduce((left, right) -> left.reduce(right))
                .orElse(InitializationStatus.ALREADY_DONE);
    }
}
