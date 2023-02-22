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

package org.apache.james.jmap.api.filtering.impl;

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.filtering.FilteringManagement;
import org.apache.james.jmap.api.filtering.Version;
import org.apache.james.user.api.UsernameChangeTaskStep;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

public class FilterUsernameChangeTaskStep implements UsernameChangeTaskStep {
    public static final Optional<Version> NO_VERSION = Optional.empty();

    private final FilteringManagement filteringManagement;

    @Inject
    public FilterUsernameChangeTaskStep(FilteringManagement filteringManagement) {
        this.filteringManagement = filteringManagement;
    }

    @Override
    public StepName name() {
        return new StepName("FilterUsernameChangeTaskStep");
    }

    @Override
    public int priority() {
        return 4;
    }

    @Override
    public Publisher<Void> changeUsername(Username oldUsername, Username newUsername) {
        return Mono.from(filteringManagement.listRulesForUser(oldUsername))
            .filter(rules -> !rules.getRules().isEmpty())
            .flatMap(rules -> Mono.from(filteringManagement.defineRulesForUser(newUsername, rules.getRules(), NO_VERSION))
                .then(Mono.from(filteringManagement.clearRulesForUser(oldUsername))))
            .then();
    }
}
