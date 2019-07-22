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

package org.apache.james.webadmin.service;

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.rrt.cassandra.CassandraMappingsSourcesDAO;
import org.apache.james.rrt.cassandra.migration.MappingsSourcesMigration;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;

import reactor.core.publisher.Mono;

public class CassandraMappingsSolveInconsistenciesTask implements Task {
    public static final String TYPE = "cassandraMappingsSolveInconsistencies";

    private final Task mappingsSourcesMigration;
    private final CassandraMappingsSourcesDAO cassandraMappingsSourcesDAO;

    @Inject
    CassandraMappingsSolveInconsistenciesTask(MappingsSourcesMigration mappingsSourcesMigration,
                                              CassandraMappingsSourcesDAO cassandraMappingsSourcesDAO) {
        this.mappingsSourcesMigration = mappingsSourcesMigration.asTask();
        this.cassandraMappingsSourcesDAO = cassandraMappingsSourcesDAO;
    }

    @Override
    public Result run() {
        return cassandraMappingsSourcesDAO.removeAllData()
            .doOnError(e -> LOGGER.error("Error while cleaning up data in mappings sources projection table"))
            .then(Mono.fromCallable(mappingsSourcesMigration::run))
            .onErrorResume(e -> Mono.just(Result.PARTIAL))
            .block();
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return mappingsSourcesMigration.details();
    }
}
