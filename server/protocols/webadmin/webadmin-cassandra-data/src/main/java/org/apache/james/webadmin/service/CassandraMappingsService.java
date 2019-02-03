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

import javax.inject.Inject;

import org.apache.james.rrt.cassandra.CassandraMappingsSourcesDAO;
import org.apache.james.rrt.cassandra.migration.MappingsSourcesMigration;
import org.apache.james.task.Task;
import org.apache.james.webadmin.dto.ActionMappings;

public class CassandraMappingsService {
    private final MappingsSourcesMigration mappingsSourcesMigration;
    private final CassandraMappingsSourcesDAO cassandraMappingsSourcesDAO;

    @Inject
    public CassandraMappingsService(MappingsSourcesMigration mappingsSourcesMigration,
                                    CassandraMappingsSourcesDAO cassandraMappingsSourcesDAO) {
        this.mappingsSourcesMigration = mappingsSourcesMigration;
        this.cassandraMappingsSourcesDAO = cassandraMappingsSourcesDAO;
    }

    public Task createActionTask(ActionMappings action) {
        switch (action) {
            case SolveInconsistencies:
                return solveMappingsSourcesInconsistencies();
            default:
                throw new IllegalArgumentException(action + " is not a supported action");
        }
    }

    private Task solveMappingsSourcesInconsistencies() {
        return new CassandraMappingsSolveInconsistenciesTask(mappingsSourcesMigration, cassandraMappingsSourcesDAO);
    }
}
