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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class GatewayTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(Gateway.class)
            .verify();
    }

    @Test
    void shouldThrowOnNullName() {
        assertThatThrownBy(() -> Gateway.builder()
                .name(null)
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowOnNullNameWhenType() {
        assertThatThrownBy(() -> Gateway.builder()
                .nameType(new AddressType("type"))
                .name(null)
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowOnNullType() {
        assertThatThrownBy(() -> Gateway.builder()
                .nameType(null)
                .name(Text.fromRawText("name"))
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void addressTypeShouldDefaultToDNS() {
        Text address = Text.fromRawText("address");
        assertThat(Gateway.builder().name(Text.fromRawText("address")).build())
            .isEqualTo(Gateway.builder().nameType(AddressType.DNS).name(address).build());
    }

    @Test
    void formattedValueShouldDisplayAddress() {
        assertThat(Gateway.builder().name(Text.fromRawText("address")).build()
            .formattedValue())
            .isEqualTo("MDN-Gateway: dns;address");
    }

    @Test
    void formattedValueShouldDisplayMultilineAddress() {
        assertThat(Gateway.builder().name(Text.fromRawText("address\nmultiline")).build()
            .formattedValue())
            .isEqualTo("MDN-Gateway: dns;address\r\n multiline");
    }

    @Test
    void formattedValueShouldDisplayCustomAddress() {
        assertThat(Gateway.builder().nameType(new AddressType("custom")).name(Text.fromRawText("address")).build()
            .formattedValue())
            .isEqualTo("MDN-Gateway: custom;address");
    }

    @Test
    void fieldValueShouldDisplayAddress() {
        assertThat(Gateway.builder()
                .name(Text.fromRawText("address"))
                .build()
                .fieldValue())
                .isEqualTo("dns;address");
    }

    @Test
    void fieldValueShouldDisplayMultilineAddress() {
        assertThat(Gateway.builder()
                .nameType(AddressType.UNKNOWN)
                .name(Text.fromRawText("address\nmultiline"))
                .build()
                .fieldValue())
                .isEqualTo("unknown;address\r\n multiline");
    }

}
