/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/

package org.apache.james.task.eventsourcing.cassandra;

import java.util.function.Supplier;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.server.task.json.JsonTaskAdditionalInformationSerializer;
import org.apache.james.server.task.json.dto.MemoryReferenceWithCounterTaskAdditionalInformationDTO;
import org.apache.james.task.eventsourcing.TaskExecutionDetailsProjection;
import org.apache.james.task.eventsourcing.TaskExecutionDetailsProjectionContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraTaskExecutionDetailsProjectionTest implements TaskExecutionDetailsProjectionContract {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
            CassandraModule.aggregateModules(CassandraSchemaVersionModule.MODULE, CassandraZonedDateTimeModule.MODULE, CassandraTaskExecutionDetailsProjectionModule.MODULE()));
    private static final JsonTaskAdditionalInformationSerializer JSON_TASK_ADDITIONAL_INFORMATION_SERIALIZER = JsonTaskAdditionalInformationSerializer.of(MemoryReferenceWithCounterTaskAdditionalInformationDTO.SERIALIZATION_MODULE);

    private Supplier<CassandraTaskExecutionDetailsProjection> testeeSupplier;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        CassandraTaskExecutionDetailsProjectionDAO cassandraTaskExecutionDetailsProjectionDAO = new CassandraTaskExecutionDetailsProjectionDAO(cassandra.getConf(), cassandra.getTypesProvider(), JSON_TASK_ADDITIONAL_INFORMATION_SERIALIZER);
        testeeSupplier = () -> new CassandraTaskExecutionDetailsProjection(cassandraTaskExecutionDetailsProjectionDAO);
    }

    @Override
    public TaskExecutionDetailsProjection testee() {
        return testeeSupplier.get();
    }

}
