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

import org.apache.james.jmap.draft.exceptions.JmapFieldNotSupportedException;
import org.apache.james.jmap.methods.JmapRequest;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.collect.ImmutableList;

@JsonDeserialize(builder = SetFilterRequest.Builder.class)
public class SetFilterRequest implements JmapRequest {

    private static final String ISSUER = "SetFilterRequest";

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private final ImmutableList.Builder<JmapRuleDTO> rules;

        private Builder() {
            this.rules = ImmutableList.builder();
        }

        public Builder accountId(String accountId) {
            if (accountId != null) {
                throw new JmapFieldNotSupportedException(ISSUER, "accountId");
            }
            return this;
        }

        public Builder ifInState(String ifInState) {
            if (ifInState != null) {
                throw new JmapFieldNotSupportedException(ISSUER, "ifInState");
            }
            return this;
        }

        public Builder singleton(List<JmapRuleDTO> rules) {
            this.rules.addAll(rules);
            return this;
        }

        public SetFilterRequest build() {
            return new SetFilterRequest(rules.build());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final List<JmapRuleDTO> singleton;

    private SetFilterRequest(List<JmapRuleDTO> singleton) {
        this.singleton = singleton;
    }

    public List<JmapRuleDTO> getSingleton() {
        return singleton;
    }
}
