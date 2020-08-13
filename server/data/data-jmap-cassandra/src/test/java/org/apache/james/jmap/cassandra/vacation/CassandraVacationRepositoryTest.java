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

package org.apache.james.jmap.cassandra.vacation;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.jmap.api.vacation.VacationRepository;
import org.apache.james.jmap.api.vacation.VacationRepositoryContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraVacationRepositoryTest implements VacationRepositoryContract {
    static final CassandraModule MODULE = CassandraModule.aggregateModules(
        CassandraSchemaVersionModule.MODULE,
        CassandraVacationModule.MODULE,
        CassandraZonedDateTimeModule.MODULE);

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MODULE);

    VacationRepository vacationRepository;

    @BeforeEach
    public void setUp(CassandraCluster cassandra) throws Exception {
        vacationRepository = new CassandraVacationRepository(new CassandraVacationDAO(cassandra.getConf(), cassandra.getTypesProvider()));
    }

    @Override
    public VacationRepository vacationRepository() {
        return vacationRepository;
    }
}
