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

package org.apache.james.model;

import java.time.Duration;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(
    using = CrowdsecDecisionDeserializer.class
)
public class CrowdsecDecision {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrowdsecDecision.class);
    public static final String BAN = "ban";

    private final Duration duration;
    private final long id;
    private final String origin;
    private final String scenario;
    private final String scope;
    private final String type;
    private final String value;

    public static Builder builder() {
        return new Builder();
    }

    private CrowdsecDecision(Duration duration, long id, String origin, String scenario, String scope, String type, String value) {
        this.id = id;
        this.origin = origin;
        this.scenario = scenario;
        this.scope = scope;
        this.type = type;
        this.value = value;
        this.duration = duration;
    }

    public final boolean equals(Object o) {
        if (!(o instanceof CrowdsecDecision)) {
            return false;
        } else {
            CrowdsecDecision that = (CrowdsecDecision)o;
            return Objects.equals(this.id, that.id) && Objects.equals(this.duration, that.duration) && Objects.equals(this.origin, that.origin) && Objects.equals(this.scenario, that.scenario) && Objects.equals(this.scope, that.scope) && Objects.equals(this.type, that.type) && Objects.equals(this.value, that.value);
        }
    }

    public final int hashCode() {
        return Objects.hash(new Object[]{this.duration, this.id, this.origin, this.scenario, this.scope, this.type, this.value});
    }

    public String toString() {
        return "CrowdsecDecision{duration=" + this.duration + ", id=" + this.id + ", origin='" + this.origin + "', scenario='" + this.scenario + "', scope='" + this.scope + "', type='" + this.type + "', value='" + this.value + "'}";
    }

    public static class Builder {
        private Duration duration;
        private long id;
        private String origin;
        private String scenario;
        private String scope;
        private String type;
        private String value;

        public Builder() {
        }

        public Builder duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public Builder id(long id) {
            this.id = id;
            return this;
        }

        public Builder origin(String origin) {
            this.origin = origin;
            return this;
        }

        public Builder scenario(String scenario) {
            this.scenario = scenario;
            return this;
        }

        public Builder scope(String scope) {
            this.scope = scope;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder value(String value) {
            this.value = value;
            return this;
        }

        public CrowdsecDecision build() {
            return new CrowdsecDecision(this.duration, this.id, this.origin, this.scenario, this.scope, this.type, this.value);
        }
    }
}
