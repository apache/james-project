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

package org.apache.james.mdn.fields;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import nl.jqno.equalsverifier.EqualsVerifier;

public class ExtensionFieldTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldMatchBeanContract() throws Exception {
        EqualsVerifier.forClass(ExtensionField.class)
            .verify();
    }

    @Test
    public void shouldThrowOnNullFieldName() {
        expectedException.expect(NullPointerException.class);

        ExtensionField.builder().fieldName(null).rawValue("rawValue").build();
    }

    @Test
    public void shouldThrowOnNullRawValue() {
        expectedException.expect(NullPointerException.class);

        ExtensionField.builder().fieldName("name").rawValue(null).build();
    }

    @Test
    public void shouldThrowOnMultilineName() {
        expectedException.expect(IllegalStateException.class);

        ExtensionField.builder().fieldName("name\nmultiline").rawValue("rawValue").build();
    }

    @Test
    public void formattedValueShouldDisplayNameAndRawValue() {
        assertThat(ExtensionField.builder().fieldName("name").rawValue("rawValue").build()
            .formattedValue())
            .isEqualTo("name: rawValue");
    }
}
