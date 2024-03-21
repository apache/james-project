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
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.lifecycle.api.StartUpCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import reactor.core.publisher.Flux;

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

        @VisibleForTesting
        public List<String> badCheckNames() {
            return badChecks.stream()
                .map(StartUpCheck.CheckResult::getName)
                .collect(ImmutableList.toImmutableList());
        }
    }

    static class StartUpChecks {

        private final Set<StartUpCheck> startUpChecks;

        @Inject
        StartUpChecks(Set<StartUpCheck> startUpChecks) {
            this.startUpChecks = startUpChecks;
        }

        public List<StartUpCheck.CheckResult> check() {
            return Flux.fromIterable(startUpChecks)
                .map(this::checkQuietly)
                .collect(ImmutableList.toImmutableList())
                .block();
        }

        private StartUpCheck.CheckResult checkQuietly(StartUpCheck startUpCheck) {
            try {
                return startUpCheck.check();
            } catch (Exception e) {
                LOGGER.error("Error during the {} check", startUpCheck.checkName(), e);
                return StartUpCheck.CheckResult.builder()
                    .checkName(startUpCheck.checkName())
                    .resultType(StartUpCheck.ResultType.BAD)
                    .description(e.getMessage())
                    .build();
            }
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
            .collect(ImmutableList.toImmutableList());

        if (!badChecks.isEmpty()) {
            throw new StartUpChecksException(badChecks);
        }

        LOGGER.info("StartUpChecks all succeeded");
    }
}
