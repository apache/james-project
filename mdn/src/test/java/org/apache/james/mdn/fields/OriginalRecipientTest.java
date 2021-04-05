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

class OriginalRecipientTest {
    static final Text ADDRESS = Text.fromRawText("address");

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(OriginalRecipient.class)
            .verify();
    }

    @Test
    void shouldThrowOnNullAddress() {
        assertThatThrownBy(() -> OriginalRecipient.builder()
                .originalRecipient(null)
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowOnNullAddressWhenCustomType() {
        assertThatThrownBy(() -> OriginalRecipient.builder()
                .addressType(new AddressType("customType"))
                .originalRecipient(null)
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowOnNullAddressType() {
        assertThatThrownBy(() -> OriginalRecipient.builder()
                .addressType(null)
                .originalRecipient(ADDRESS)
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void addressTypeShouldDefaultToRfc822() {
        assertThat(OriginalRecipient.builder()
                .originalRecipient(ADDRESS)
                .build())
            .isEqualTo(OriginalRecipient.builder()
                .addressType(AddressType.RFC_822)
                .originalRecipient(ADDRESS)
                .build());
    }

    @Test
    void formattedValueShouldDisplayAddress() {
        assertThat(OriginalRecipient.builder()
                .originalRecipient(ADDRESS)
                .build()
                .formattedValue())
            .isEqualTo("Original-Recipient: rfc822; address");
    }

    @Test
    void formattedValueShouldDisplayCustomType() {
        assertThat(OriginalRecipient.builder()
                .addressType(new AddressType("custom"))
                .originalRecipient(ADDRESS)
                .build()
                .formattedValue())
            .isEqualTo("Original-Recipient: custom; address");
    }

    @Test
    void formattedValueShouldDisplayMultilineAddress() {
        assertThat(OriginalRecipient.builder()
                .originalRecipient(Text.fromRawText("multiline\naddress"))
                .build()
                .formattedValue())
            .isEqualTo("Original-Recipient: rfc822; multiline\r\n address");
    }

    @Test
    void fieldValueShouldDontHaveSemiColonWhenAgentProductIsNull() {
        assertThat(OriginalRecipient.builder()
            .originalRecipient(Text.fromRawText("multiline\naddress"))
            .build()
            .fieldValue())
            .isEqualTo("rfc822; multiline\r\n address");
    }

    @Test
    void fieldValueShouldSuccessWithFullProperties() {
        assertThat(OriginalRecipient.builder()
            .originalRecipient(Text.fromRawText("multiline\naddress"))
            .addressType(new AddressType("address"))
            .build()
            .fieldValue())
            .isEqualTo("address; multiline\r\n address");
    }

    @Test
    void fieldValueShouldFailWhenAgentNameIsNull() {
        assertThatThrownBy(() -> OriginalRecipient.builder()
            .build())
            .isInstanceOf(NullPointerException.class);
    }
}
