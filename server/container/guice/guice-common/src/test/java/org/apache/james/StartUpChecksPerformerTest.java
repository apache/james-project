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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.StartUpChecksPerformer.StartUpCheck.CheckResult;
import org.apache.james.StartUpChecksPerformer.StartUpCheck.ResultType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class StartUpChecksPerformerTest {

    private static final CheckResult GOOD_CHECK_1 = CheckResult.builder()
        .checkName("good 1")
        .resultType(ResultType.GOOD)
        .build();
    private static final CheckResult GOOD_CHECK_2 = CheckResult.builder()
        .checkName("good 2")
        .resultType(ResultType.GOOD)
        .build();
    private static final CheckResult BAD_CHECK_1 = CheckResult.builder()
        .checkName("bad 1")
        .resultType(ResultType.BAD)
        .build();
    private static final CheckResult BAD_CHECK_2 = CheckResult.builder()
        .checkName("bad 2")
        .resultType(ResultType.BAD)
        .build();

    private static StartUpChecksPerformer.StartUpCheck fromResult(CheckResult checkResult) {
        return new StartUpChecksPerformer.StartUpCheck() {

            @Override
            public CheckResult check() {
                return checkResult;
            }

            @Override
            public String checkName() {
                return checkResult.getName();
            }
        };
    }

    @Test
    void performCheckShouldNotThrowWhenAllChecksSucceed() {
        StartUpChecksPerformer checksPerformer = StartUpChecksPerformer.from(
            fromResult(GOOD_CHECK_1),
            fromResult(GOOD_CHECK_2));

        assertThatCode(checksPerformer::performCheck)
            .doesNotThrowAnyException();
    }

    @Test
    void performCheckShouldNotThrowWhenNoChecks() {
        StartUpChecksPerformer checksPerformer = StartUpChecksPerformer.from();

        assertThatCode(checksPerformer::performCheck)
            .doesNotThrowAnyException();
    }

    @Test
    void performCheckShouldThrowWhenThereIsOneCheckFails() {
        StartUpChecksPerformer checksPerformer = StartUpChecksPerformer.from(
            fromResult(GOOD_CHECK_1),
            fromResult(GOOD_CHECK_2),
            fromResult(BAD_CHECK_1));

        assertThatThrownBy(checksPerformer::performCheck)
            .isInstanceOf(StartUpChecksPerformer.StartUpChecksException.class);
    }

    @Test
    void performCheckShouldThrowAnExceptionContainingAllBadChecksWhenThereAreBadChecks() {
        StartUpChecksPerformer checksPerformer = StartUpChecksPerformer.from(
            fromResult(GOOD_CHECK_1),
            fromResult(GOOD_CHECK_2),
            fromResult(BAD_CHECK_1),
            fromResult(BAD_CHECK_2));

        assertThatThrownBy(checksPerformer::performCheck)
            .isInstanceOfSatisfying(
                StartUpChecksPerformer.StartUpChecksException.class,
                exception -> assertThat(exception.getBadChecks())
                    .containsOnly(BAD_CHECK_1, BAD_CHECK_2));
    }

    @Disabled("performCheck() now doesn't support this")
    @Test
    void performCheckShouldNotPropagateUnExpectedExceptionDuringChecking() {
        String checkName = "throwing check name";
        StartUpChecksPerformer checksPerformer = StartUpChecksPerformer.from(

            new StartUpChecksPerformer.StartUpCheck() {
                @Override
                public CheckResult check() {
                    throw new RuntimeException("unexpected");
                }

                @Override
                public String checkName() {
                    return checkName;
                }
            });

        assertThatThrownBy(checksPerformer::performCheck)
            .isInstanceOfSatisfying(
                StartUpChecksPerformer.StartUpChecksException.class,
                exception -> assertThat(exception.badCheckNames())
                    .containsOnly(checkName));
    }
}