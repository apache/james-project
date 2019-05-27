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

package org.apache.james.lifecycle.api;

import java.util.Objects;
import java.util.Optional;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public interface StartUpCheck {

    enum ResultType {
        GOOD, BAD
    }

    class CheckResult {

        public static class Builder {

            @FunctionalInterface
            public interface RequireCheckName {
                RequireResultType checkName(String name);
            }

            @FunctionalInterface
            public interface RequireResultType {
                ReadyToBuild resultType(ResultType resultType);
            }

            public static class ReadyToBuild {
                private final String name;
                private final ResultType resultType;
                private Optional<String> description;

                ReadyToBuild(String name, ResultType resultType) {
                    this.name = name;
                    this.resultType = resultType;
                    this.description = Optional.empty();
                }

                public ReadyToBuild description(String description) {
                    this.description = Optional.ofNullable(description);
                    return this;
                }

                public CheckResult build() {
                    return new CheckResult(name, resultType, description);
                }
            }

        }

        public static Builder.RequireCheckName builder() {
            return name -> resultType -> new Builder.ReadyToBuild(name, resultType);
        }

        private final String name;
        private final ResultType resultType;
        private final Optional<String> description;

        private CheckResult(String name, ResultType resultType, Optional<String> description) {
            Preconditions.checkNotNull(name);
            Preconditions.checkNotNull(resultType);
            Preconditions.checkNotNull(description);

            this.name = name;
            this.resultType = resultType;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public ResultType getResultType() {
            return resultType;
        }

        public Optional<String> getDescription() {
            return description;
        }

        public boolean isBad() {
            return resultType.equals(ResultType.BAD);
        }

        public boolean isGood() {
            return resultType.equals(ResultType.GOOD);
        }


        @Override
        public final boolean equals(Object o) {
            if (o instanceof CheckResult) {
                CheckResult that = (CheckResult) o;

                return Objects.equals(this.name, that.name)
                    && Objects.equals(this.resultType, that.resultType)
                    && Objects.equals(this.description, that.description);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(name, resultType, description);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("resultType", resultType)
                .add("description", description)
                .toString();
        }
    }

    CheckResult check();

    String checkName();
}
