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

package org.apache.james.util.streams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;

class LimitTest {

    private final List<Integer> aList = ImmutableList.of(1, 2, 3, 4, 5, 6);

    @Test
    void unlimitedShouldCreateLimitWithNoLimit() {
        Limit testee = Limit.unlimited();
        assertThat(testee.getLimit()).isEqualTo(Optional.empty());
    }

    @Test
    void beanShouldRespectBeanContract() {
        EqualsVerifier.forClass(Limit.class)
            .verify();
    }

    @Test
    void unlimitedShouldCreateLimitThatDoesNotAffectStream() {

        Limit testee = Limit.unlimited();
        assertThat(
            testee
                .applyOnStream(aList.stream())
                .collect(Guavate.toImmutableList())
        ).isEqualTo(aList);
    }

    @Test
    void limitShouldCreateLimitWithNoLimit() {
        int expected = 3;

        Limit testee = Limit.limit(expected);
        assertThat(testee.getLimit())
            .isEqualTo(Optional.of(expected));
    }

    @Test
    void limitShouldCreateLimitThatCorrectlyTruncateStream() {
        Limit testee = Limit.limit(3);

        assertThat(testee
            .applyOnStream(aList.stream())
            .collect(Guavate.toImmutableList())
        ).isEqualTo(ImmutableList.of(1, 2, 3));
    }

    @Test
    void limitShouldThrowAnErrorWhenCalledWithZero() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Limit.limit(0));
    }


    @Test
    void limitShouldThrowAnErrorWhenCalledWithNegativeValue() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Limit.limit(-1));
    }

    @Test
    void ofShouldTakePositiveValueAsLimit() {
        assertThat(Limit.from(3))
            .isEqualTo(Limit.limit(3));
    }

    @Test
    void ofShouldTakeNegativeValueAsUnlimited() {
        assertThat(Limit.from(-1))
            .isEqualTo(Limit.unlimited());
    }

    @Test
    void ofShouldTakeZeroValueAsUnlimited() {
        assertThat(Limit.from(0))
            .isEqualTo(Limit.unlimited());
    }
}
