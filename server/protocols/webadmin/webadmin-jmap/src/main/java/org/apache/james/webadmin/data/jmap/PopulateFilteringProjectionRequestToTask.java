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

import static org.apache.james.webadmin.data.jmap.Constants.POPULATE_FILTERING_PROJECTION;

import javax.inject.Inject;

import org.apache.james.jmap.api.filtering.impl.EventSourcingFilteringManagement;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;

public class PopulateFilteringProjectionRequestToTask extends TaskFromRequestRegistry.TaskRegistration {
    @Inject
    PopulateFilteringProjectionRequestToTask(EventSourcingFilteringManagement.NoReadProjection noReadProjection,
                                             EventSourcingFilteringManagement.ReadProjection readProjection,
                                             UsersRepository usersRepository) {
        super(POPULATE_FILTERING_PROJECTION,
            request -> new PopulateFilteringProjectionTask(noReadProjection, readProjection, usersRepository));
    }
}
