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

package org.apache.james.mock.smtp.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OperatorTest {

    @Nested
    class ContainsTest {
        @Test
        void containsShouldReturnTrueWhenActualContainsExpected() {
            assertThat(Operator.CONTAINS
                    .actual("this contains matchme string")
                    .expected("matchme")
                    .matches())
                .isTrue();
        }

        @Test
        void containsShouldReturnTrueWhenBothValuesAreEqual() {
            assertThat(Operator.CONTAINS
                    .actual("matchme")
                    .expected("matchme")
                    .matches())
                .isTrue();
        }

        @Test
        void containsShouldReturnFalseWhenActualDoesNotContainExpected() {
            assertThat(Operator.CONTAINS
                    .actual("this contains an other string")
                    .expected("matchme")
                    .matches())
                .isFalse();
        }

        @Test
        void containsShouldReturnFalseWhenExpectedContainsActual() {
            assertThat(Operator.CONTAINS
                    .actual("matchme")
                    .expected("this contains matchme")
                    .matches())
                .isFalse();
        }

        @Test
        void containsShouldBeCaseSensitive() {
            assertThat(Operator.CONTAINS.actual("this contains matchme string")
                    .expected("Matchme")
                    .matches())
                .isFalse();
        }

        @Test
        void containsShouldThrowOnNullActual() {
            assertThatThrownBy(() -> Operator.CONTAINS
                    .actual(null)
                    .expected("matchme")
                    .matches())
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void containsShouldThrowOnNullExpected() {
            assertThatThrownBy(() -> Operator.CONTAINS
                    .actual("this contains matchme string")
                    .expected(null)
                    .matches())
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void containsShouldReturnTrueWhenExpectedIsEmpty() {
            assertThat(Operator.CONTAINS
                    .actual("this contains matchme string")
                    .expected("")
                    .matches())
                .isTrue();
        }

        @Test
        void containsShouldReturnFalseWhenActualIsEmpty() {
            assertThat(Operator.CONTAINS
                    .actual("")
                    .expected("matchme")
                    .matches())
                .isFalse();
        }

        @Test
        void containsShouldReturnTrueWhenBothValuesAreEmpty() {
            assertThat(Operator.CONTAINS
                    .actual("")
                    .expected("")
                    .matches())
                .isTrue();
        }
    }
}
