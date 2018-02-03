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

package org.apache.james.webadmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class FixedPortSupplierTest {

    @Test
    public void toIntShouldThrowOnNegativePort() {
        assertThatThrownBy(() -> new FixedPortSupplier(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void toIntShouldThrowOnNullPort() {
        assertThatThrownBy(() -> new FixedPortSupplier(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void toIntShouldThrowOnTooBigNumbers() {
        assertThatThrownBy(() -> new FixedPortSupplier(65536)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void toIntShouldReturnedDesiredPort() {
        int expectedPort = 452;
        assertThat(new FixedPortSupplier(expectedPort).get().getValue()).isEqualTo(expectedPort);
    }

    @Test
    public void shouldMatchBeanContract() {
        EqualsVerifier.forClass(FixedPortSupplier.class).verify();
    }

}
