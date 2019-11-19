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

import java.util.Optional;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class OffsetTest {

    public static final int VALUE = 18;

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(Offset.class)
            .verify();
    }

    @Test
    void fromZeroShouldBeEquivalentToNone() {
        assertThat(Offset.from(0))
            .isEqualTo(Offset.none());
    }

    @Test
    void getOffsetShouldReturnContainedValue() {
        assertThat(Offset.from(VALUE).getOffset())
            .isEqualTo(VALUE);
    }

    @Test
    void fromOptionalShouldBeEquivalentToFromValueWhenPresent() {
        assertThat(Offset.from(Optional.of(VALUE)))
            .isEqualTo(Offset.from(VALUE));
    }

}
