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

package org.apache.james.webadmin.data.jmap;

import static org.mockito.Mockito.mock;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.jmap.api.filtering.impl.EventSourcingFilteringManagement;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PopulateFilteringProjectionTaskSerializationTest {
    private EventSourcingFilteringManagement.NoReadProjection noReadProjection;
    private EventSourcingFilteringManagement.ReadProjection readProjection;
    private UsersRepository usersRepository;

    @BeforeEach
    void setUp() {
        noReadProjection = new EventSourcingFilteringManagement.NoReadProjection(mock(EventStore.class));
        readProjection = mock(EventSourcingFilteringManagement.ReadProjection.class);
        usersRepository = mock(UsersRepository.class);
    }

    @Test
    void shouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(PopulateFilteringProjectionTask.module(noReadProjection, readProjection, usersRepository))
            .bean(new PopulateFilteringProjectionTask(noReadProjection, readProjection, usersRepository))
            .json(ClassLoaderUtils.getSystemResourceAsString("json/populateFilters.task.json"))
            .verify();
    }
}