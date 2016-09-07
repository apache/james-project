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

package org.apache.james.transport.mailets;

import java.util.List;

import com.google.common.collect.ImmutableList;

public class ReplaceConfig {

    public static ReplaceConfig.Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ImmutableList.Builder<ReplacingPattern> subjectReplacingUnits;
        private ImmutableList.Builder<ReplacingPattern> bodyReplacingUnits;

        private Builder() {
            subjectReplacingUnits = ImmutableList.builder();
            bodyReplacingUnits = ImmutableList.builder();
        }

        public ReplaceConfig.Builder addAllSubjectReplacingUnits(List<ReplacingPattern> subjectReplacingUnits) {
            this.subjectReplacingUnits.addAll(subjectReplacingUnits);
            return this;
        }

        public ReplaceConfig.Builder addAllBodyReplacingUnits(List<ReplacingPattern> bodyReplacingUnits) {
            this.bodyReplacingUnits.addAll(bodyReplacingUnits);
            return this;
        }

        public ReplaceConfig build() {
            return new ReplaceConfig(subjectReplacingUnits.build(), bodyReplacingUnits.build());
        }
    }

    private final List<ReplacingPattern> subjectReplacingUnits;
    private final List<ReplacingPattern> bodyReplacingUnits;

    public ReplaceConfig(List<ReplacingPattern> subjectReplacingUnits, List<ReplacingPattern> bodyReplacingUnits) {
        this.subjectReplacingUnits = subjectReplacingUnits;
        this.bodyReplacingUnits = bodyReplacingUnits;
    }

    public List<ReplacingPattern> getSubjectReplacingUnits() {
        return subjectReplacingUnits;
    }

    public List<ReplacingPattern> getBodyReplacingUnits() {
        return bodyReplacingUnits;
    }
}