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

package org.apache.james.jmap.draft.model;

import java.util.List;

import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.methods.Method;

import com.google.common.collect.ImmutableList;

public class GetFilterResponse implements Method.Response {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String accountId;
        private String state;
        private ImmutableList.Builder<JmapRuleDTO> rules;

        public Builder() {
            this.rules = ImmutableList.builder();
        }

        public Builder accountId(String accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder state(String state) {
            this.state = state;
            return this;
        }

        public Builder rules(List<Rule> rules) {
            this.rules.addAll(rules.stream()
                .map(JmapRuleDTO::from)
                .collect(ImmutableList.toImmutableList()));
            return this;
        }

        public GetFilterResponse build() {
            return new GetFilterResponse(accountId, state, rules.build());
        }
    }

    private final String accountId;
    private final String state;
    private final List<JmapRuleDTO> rules;

    private GetFilterResponse(String accountId, String state, List<JmapRuleDTO> rules) {
        this.accountId = accountId;
        this.state = state;
        this.rules = rules;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getState() {
        return state;
    }

    public List<JmapRuleDTO> getSingleton() {
        return rules;
    }
}
