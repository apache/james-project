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

package org.apache.james.mailbox.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class UidValidityTest {
    @Test
    void shouldRespectBeanContract() {
        EqualsVerifier.forClass(UidValidity.class)
            .verify();
    }

    @Test
    void ofShouldReturnValidUidValidity() {
        long expectedValue = 123456789L;
        UidValidity uidValidity = UidValidity.of(expectedValue);
        assertThat(uidValidity.asLong()).isEqualTo(expectedValue);
    }

    @Test
    void zeroUidValidityShouldBeInvalid() {
        assertThat(UidValidity.of(0).isValid()).isFalse();
    }

    @Test
    void negativeUidValidityShouldBeInvalid() {
        assertThat(UidValidity.of(-1).isValid()).isFalse();
    }

    @Test
    void tooBigUidValidityShouldBeInvalid() {
        assertThat(UidValidity.of(4294967296L).isValid()).isFalse();
    }

    @Test
    void idValidityShouldBeValid() {
        assertThat(UidValidity.of(42).isValid()).isTrue();
    }

    @Test
    void fromSupplierShouldNotThrowWhenZeroIsGenerated() {
        assertThatCode(() -> UidValidity.fromSupplier(() -> 0L))
            .doesNotThrowAnyException();
    }

    @Test
    void fromSupplierShouldNotThrowWhenNegativeIsGenerated() {
        assertThatCode(() -> UidValidity.fromSupplier(() -> -42L))
            .doesNotThrowAnyException();
    }

    @Test
    void fromSupplierShouldNotThrowWhenUpperBoundExclusiveIsGenerated() {
        assertThatCode(() -> UidValidity.fromSupplier(() -> 4294967296L))
            .doesNotThrowAnyException();
    }

    @Test
    void fromSupplierShouldNotThrowWhenUpperBoundInclusiveIsGenerated() {
        assertThatCode(() -> UidValidity.fromSupplier(() -> 4294967295L))
            .doesNotThrowAnyException();
    }

    @Test
    void fromSupplierShouldNotThrowWhenHigherThanUpperBoundIsGenerated() {
        assertThatCode(() -> UidValidity.fromSupplier(() -> 4294967297L))
            .doesNotThrowAnyException();
    }

    @Test
    void fromSupplierShouldNotThrowWhenLowerThanUpperBoundIsGenerated() {
        assertThatCode(() -> UidValidity.fromSupplier(() -> 4294967294L))
            .doesNotThrowAnyException();
    }

    // Sampling test
    @RepeatedTest(10000)
    void randomShouldGenerateValidValues() {
        assertThatCode(UidValidity::random)
            .doesNotThrowAnyException();
    }

    @Disabled("On average upon generating 1.000.000 UidValidity we notice 125 duplicates")
    @Test
    void randomShouldNotLeadToCollision() {
        int count = 1000000;
        long distinctCount = IntStream.range(0, count)
            .mapToObj(any -> UidValidity.random())
            .distinct()
            .count();

        assertThat(distinctCount).isEqualTo(count);
    }
}
