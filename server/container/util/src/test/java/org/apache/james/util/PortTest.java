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

package org.apache.james.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PortTest {
    @Test
    void assertValidShouldThrowOnNegativePort() {
        assertThatThrownBy(() -> Port.assertValid(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assertValidShouldThrowOnZeroPort() {
        assertThatThrownBy(() -> Port.assertValid(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assertValidShouldAcceptOne() {
        Port.assertValid(1);
    }

    @Test
    void assertValidShouldAcceptMaxValue() {
        Port.assertValid(Port.MAX_PORT_VALUE);
    }

    @Test
    void assertValidShouldThrowOnTooBigValue() {
        assertThatThrownBy(() -> Port.assertValid(Port.MAX_PORT_VALUE + 1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isValidShouldReturnFalseWhenNegative() {
        assertThat(Port.isValid(-1))
            .isFalse();
    }

    @Test
    void isValidShouldReturnFalseWhenZero() {
        assertThat(Port.isValid(0))
            .isFalse();
    }

    @Test
    void isValidShouldReturnTrueWhenOne() {
        assertThat(Port.isValid(1))
            .isTrue();
    }

    @Test
    void isValidShouldReturnTrueWhenMaxValue() {
        assertThat(Port.isValid(Port.MAX_PORT_VALUE))
            .isTrue();
    }

    @Test
    void isValidShouldReturnFalseWhenAboveMaxValue() {
        assertThat(Port.isValid(Port.MAX_PORT_VALUE + 1))
            .isFalse();
    }

    @Test
    void generateValidUnprivilegedPortShouldReturnAValidPort() {
        assertThat(Port.generateValidUnprivilegedPort())
            .isBetween(Port.PRIVILEGED_PORT_BOUND, Port.MAX_PORT_VALUE);
    }

}
