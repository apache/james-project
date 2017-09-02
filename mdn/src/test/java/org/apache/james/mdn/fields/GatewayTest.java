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

public class GatewayTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldMatchBeanContract() throws Exception {
        EqualsVerifier.forClass(Gateway.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    public void shouldThrowOnNullName() {
        expectedException.expect(NullPointerException.class);

        Text name = null;
        new Gateway(name);
    }

    @Test
    public void shouldThrowOnNullNameWhenType() {
        expectedException.expect(NullPointerException.class);

        Text name = null;
        new Gateway(new AddressType("type"), name);
    }

    @Test
    public void shouldThrowOnNullType() {
        expectedException.expect(NullPointerException.class);

        AddressType nameType = null;
        new Gateway(nameType, Text.fromRawText("name"));
    }

    @Test
    public void addressTypeSHouldDefaultToDNS() {
        Text address = Text.fromRawText("address");
        assertThat(new Gateway(address))
            .isEqualTo(new Gateway(AddressType.DNS, address));
    }

    @Test
    public void formattedValueShouldDisplayAddress() {
        assertThat(new Gateway(Text.fromRawText("address"))
            .formattedValue())
            .isEqualTo("MDN-Gateway: dns;address");
    }

    @Test
    public void formattedValueShouldDisplayMultilineAddress() {
        assertThat(new Gateway(Text.fromRawText("address\nmultiline"))
            .formattedValue())
            .isEqualTo("MDN-Gateway: dns;address\r\n" +
                " multiline");
    }

    @Test
    public void formattedValueShouldDisplayCustomAddress() {
        assertThat(new Gateway(new AddressType("custom"), Text.fromRawText("address"))
            .formattedValue())
            .isEqualTo("MDN-Gateway: custom;address");
    }
}
