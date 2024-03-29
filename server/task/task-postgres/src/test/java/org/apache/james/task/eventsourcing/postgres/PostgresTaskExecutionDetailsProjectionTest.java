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

package org.apache.james.task.eventsourcing.postgres;

import java.util.function.Supplier;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.server.task.json.JsonTaskAdditionalInformationSerializer;
import org.apache.james.server.task.json.dto.MemoryReferenceWithCounterTaskAdditionalInformationDTO;
import org.apache.james.task.eventsourcing.TaskExecutionDetailsProjection;
import org.apache.james.task.eventsourcing.TaskExecutionDetailsProjectionContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class PostgresTaskExecutionDetailsProjectionTest implements TaskExecutionDetailsProjectionContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresTaskExecutionDetailsProjectionModule.MODULE());

    private static final JsonTaskAdditionalInformationSerializer JSON_TASK_ADDITIONAL_INFORMATION_SERIALIZER = JsonTaskAdditionalInformationSerializer.of(MemoryReferenceWithCounterTaskAdditionalInformationDTO.SERIALIZATION_MODULE);

    private Supplier<PostgresTaskExecutionDetailsProjection> testeeSupplier;

    @BeforeEach
    void setUp() {
        PostgresTaskExecutionDetailsProjectionDAO dao = new PostgresTaskExecutionDetailsProjectionDAO(postgresExtension.getPostgresExecutor(),
            JSON_TASK_ADDITIONAL_INFORMATION_SERIALIZER);
        testeeSupplier = () -> new PostgresTaskExecutionDetailsProjection(dao);
    }

    @Override
    public TaskExecutionDetailsProjection testee() {
        return testeeSupplier.get();
    }

}
