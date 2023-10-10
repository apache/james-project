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

package org.apache.james.utils;

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.filtering.FilteringManagement;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.api.filtering.Version;

import reactor.core.publisher.Mono;

public class FilteringManagementProbeImpl implements GuiceProbe {

    private final FilteringManagement filteringManagement;

    @Inject
    public FilteringManagementProbeImpl(FilteringManagement filteringManagement) {
        this.filteringManagement = filteringManagement;
    }

    public void defineRulesForUser(Username username, Optional<Version> ifInState, Rule... rules) {
        Mono.from(filteringManagement.defineRulesForUser(username, ifInState, rules)).block();
    }
}
