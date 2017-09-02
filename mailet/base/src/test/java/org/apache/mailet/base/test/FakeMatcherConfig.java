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

package org.apache.mailet.base.test;

import java.util.Optional;

import org.apache.mailet.MailetContext;
import org.apache.mailet.MatcherConfig;

import com.google.common.base.Preconditions;

/**
 * MatcherConfig
 */
public class FakeMatcherConfig implements MatcherConfig {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String matcherName;
        private Optional<MailetContext> mailetContext;
        private Optional<String> condition;

        private Builder() {
            condition = Optional.empty();
            mailetContext = Optional.empty();
        }

        public Builder matcherName(String matcherName) {
            this.matcherName = matcherName;
            return this;
        }

        public Builder mailetContext(MailetContext mailetContext) {
            Preconditions.checkNotNull(mailetContext);
            this.mailetContext = Optional.of(mailetContext);
            return this;
        }

        public Builder condition(String condition) {
            this.condition = Optional.ofNullable(condition);
            return this;
        }

        public FakeMatcherConfig build() {
            Preconditions.checkNotNull(matcherName, "'matcherName' is mandatory");
            return new FakeMatcherConfig(matcherName, mailetContext.orElse(FakeMailContext.defaultContext()), condition);
        }
    }

    private final String matcherName;
    private final MailetContext mailetContext;
    private final Optional<String> condition;

    private FakeMatcherConfig(String matcherName, MailetContext mailetContext, Optional<String> condition) {
        this.matcherName = matcherName;
        this.mailetContext = mailetContext;
        this.condition = condition;
    }

    @Override
    public String getMatcherName() {
        return matcherName;
    }

    @Override
    public MailetContext getMailetContext() {
        return mailetContext;
    }

    @Override
    public String getCondition() {
        return condition.orElse(null);
    }
}