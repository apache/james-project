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

package org.apache.james;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

public class StartUpChecksPerformer {

    public static class StartUpChecksException extends Exception {

        private static String badChecksToString(List<StartUpCheck.CheckResult> badChecks) {
            Preconditions.checkArgument(!badChecks.isEmpty(), "'badChecks' should not be empty");
            Preconditions.checkArgument(badChecks
                .stream()
                .noneMatch(StartUpCheck.CheckResult::isGood),
                "'badChecks' should not have any good check");

            return Joiner.on("\n")
                .join(badChecks);
        }

        private final List<StartUpCheck.CheckResult> badChecks;

        StartUpChecksException(List<StartUpCheck.CheckResult> badChecks) {
            super("StartUpChecks got bad results: " + badChecksToString(badChecks));

            this.badChecks = badChecks;
        }

        @VisibleForTesting
        public List<StartUpCheck.CheckResult> getBadChecks() {
            return badChecks;
        }
    }

    public interface StartUpCheck {

        enum ResultType {
            GOOD, BAD
        }

        class CheckResult {

            static class Builder {

                @FunctionalInterface
                interface RequireCheckName {
                    RequireResultType checkName(String name);
                }

                @FunctionalInterface
                interface RequireResultType {
                    ReadyToBuild resultType(ResultType resultType);
                }

                static class ReadyToBuild {
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
            public String toString() {
                return MoreObjects.toStringHelper(this)
                    .add("name", name)
                    .add("resultType", resultType)
                    .add("description", description)
                    .toString();
            }
        }

        CheckResult check();
    }

    static class StartUpChecks {

        private final Set<StartUpCheck> startUpChecks;

        @Inject
        StartUpChecks(Set<StartUpCheck> startUpChecks) {
            this.startUpChecks = startUpChecks;
        }

        public List<StartUpCheck.CheckResult> check() {
            return Flux.fromIterable(startUpChecks)
                .publishOn(Schedulers.elastic())
                .map(StartUpCheck::check)
                .collect(Guavate.toImmutableList())
                .block();
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(StartUpChecksPerformer.class);

    @VisibleForTesting
    static StartUpChecksPerformer from(StartUpCheck... checks) {
        return new StartUpChecksPerformer(new StartUpChecks(Sets.newHashSet(checks)));
    }

    private final StartUpChecks startUpChecks;

    @Inject
    public StartUpChecksPerformer(StartUpChecks startUpChecks) {
        this.startUpChecks = startUpChecks;
    }

    public void performCheck() throws StartUpChecksException {
        List<StartUpCheck.CheckResult> badChecks = startUpChecks.check()
            .stream()
            .filter(StartUpCheck.CheckResult::isBad)
            .collect(Guavate.toImmutableList());

        if (!badChecks.isEmpty()) {
            throw new StartUpChecksException(badChecks);
        }

        LOGGER.info("StartUpChecks all succeeded");
    }
}
