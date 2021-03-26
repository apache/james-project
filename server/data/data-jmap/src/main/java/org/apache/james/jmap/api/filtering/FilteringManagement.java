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

package org.apache.james.jmap.api.filtering;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.james.core.Username;
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableList;

public interface FilteringManagement {

    Publisher<Version> defineRulesForUser(Username username, List<Rule> rules, Optional<Version> ifInState);

    default Publisher<Version> defineRulesForUser(Username username, Optional<Version> ifInState, Rule... rules) {
        return defineRulesForUser(username, Arrays.asList(rules), ifInState);
    }

    default Publisher<Version> clearRulesForUser(Username username, Optional<Version> ifInState) {
        return defineRulesForUser(username, ImmutableList.of(), ifInState);
    }

    Publisher<Rules> listRulesForUser(Username username);

}
