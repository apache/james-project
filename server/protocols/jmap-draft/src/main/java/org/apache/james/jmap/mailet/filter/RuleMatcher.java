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

package org.apache.james.jmap.mailet.filter;

import java.util.List;
import java.util.stream.Stream;

import org.apache.james.jmap.api.filtering.Rule;
import org.apache.mailet.Mail;

import com.google.common.base.Preconditions;

class RuleMatcher {
    private final List<Rule> filteringRules;

    RuleMatcher(List<Rule> filteringRules) {
        Preconditions.checkNotNull(filteringRules);

        this.filteringRules = filteringRules;
    }

    Stream<Rule> findApplicableRules(Mail mail) {
        return filteringRules.stream()
            .filter(rule -> MailMatcher.from(rule).match(mail));
    }
}
