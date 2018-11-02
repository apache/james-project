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
package org.apache.mailet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class AttributeTest {

    @Test
    void shouldRespectBeanContract() {
        EqualsVerifier.forClass(Attribute.class).verify();
    }

    @Test
    void constructorShouldThrowOnNullName() {
        assertThatNullPointerException()
            .isThrownBy(() -> new Attribute(null, AttributeValue.of(1)));
    }

    @Test
    void constructorShouldThrowOnNullValue() {
        assertThatNullPointerException()
            .isThrownBy(() -> new Attribute(AttributeName.of("name"), null));
    }

    @Test
    void convertToAttributeShouldReturnCorrespondingAttribute() {
        assertThat(Attribute.convertToAttribute("name", "value"))
            .isEqualTo(new Attribute(
                AttributeName.of("name"),
                AttributeValue.of("value")));
    }

}
