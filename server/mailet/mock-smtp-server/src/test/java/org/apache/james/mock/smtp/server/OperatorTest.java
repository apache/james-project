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
        void containsShouldReturnTrueWhenTestedValueContainsReferenceValue() {
            assertThat(Operator.CONTAINS.matches("this contains matchme string", "matchme"))
                .isTrue();
        }

        @Test
        void containsShouldReturnTrueWhenBothValuesAreEqual() {
            assertThat(Operator.CONTAINS.matches("matchme", "matchme"))
                .isTrue();
        }

        @Test
        void containsShouldReturnFalseWhenTestedValueDoesNotContainReferenceValue() {
            assertThat(Operator.CONTAINS.matches("this contains an other string", "matchme"))
                .isFalse();
        }

        @Test
        void containsShouldReturnFalseWhenReferenceValueContainsTestedValue() {
            assertThat(Operator.CONTAINS.matches("matchme", "this contains matchme"))
                .isFalse();
        }

        @Test
        void containsShouldBeCaseSensitive() {
            assertThat(Operator.CONTAINS.matches("this contains matchme string", "Matchme"))
                .isFalse();
        }

        @Test
        void containsShouldThrowOnNullTestedValue() {
            assertThatThrownBy(() -> Operator.CONTAINS.matches(null, "matchme"))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void containsShouldThrowOnNullReferenceValue() {
            assertThatThrownBy(() -> Operator.CONTAINS.matches("this contains matchme string", null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void containsShouldReturnTrueWhenReferenceValueIsEmpty() {
            assertThat(Operator.CONTAINS.matches("this contains matchme string", ""))
                .isTrue();
        }

        @Test
        void containsShouldReturnFalseWhenTestedValueIsEmpty() {
            assertThat(Operator.CONTAINS.matches("", "matchme"))
                .isFalse();
        }

        @Test
        void containsShouldReturnTrueWhenBothValuesAreEmpty() {
            assertThat(Operator.CONTAINS.matches("", ""))
                .isTrue();
        }
    }
}
