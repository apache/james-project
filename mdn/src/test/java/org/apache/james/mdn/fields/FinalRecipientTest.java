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

class FinalRecipientTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(FinalRecipient.class)
            .verify();
    }

    @Test
    void shouldThrowOnNullAddress() {
        assertThatThrownBy(() -> FinalRecipient.builder()
                .finalRecipient(null)
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowOnNullAddressWithType() {
        assertThatThrownBy(() -> FinalRecipient.builder()
                .addressType(new AddressType("customType"))
                .finalRecipient(null)
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowOnNullType() {
        assertThatThrownBy(() -> FinalRecipient.builder()
                .addressType(null)
                .finalRecipient(Text.fromRawText("address"))
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void typeShouldDefaultToRfc822() {
        Text address = Text.fromRawText("address");

        assertThat(FinalRecipient.builder()
                .finalRecipient(address)
                .build())
            .isEqualTo(FinalRecipient.builder()
                .addressType(AddressType.RFC_822)
                .finalRecipient(address)
                .build());
    }

    @Test
    void formattedValueShouldDisplayAddress() {
        assertThat(FinalRecipient.builder()
                .finalRecipient(Text.fromRawText("Plop"))
                .build()
                .formattedValue())
            .isEqualTo("Final-Recipient: rfc822; Plop");
    }

    @Test
    void formattedValueShouldDisplayCustomType() {
        assertThat(FinalRecipient.builder()
                .addressType(new AddressType("postal"))
                .finalRecipient(Text.fromRawText("Plop"))
                .build()
                .formattedValue())
            .isEqualTo("Final-Recipient: postal; Plop");
    }

    @Test
    void formattedValueShouldDisplayMultilineAddress() {
        assertThat(FinalRecipient.builder()
                .finalRecipient(Text.fromRawText("Plop\nGlark"))
                .build()
                .formattedValue())
            .isEqualTo("Final-Recipient: rfc822; Plop\r\n Glark");
    }

    @Test
    void fieldValueShouldThrowWhenFinalRecipientIsNull() {
        assertThatThrownBy(() -> FinalRecipient.builder()
            .build()
            .fieldValue())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fieldValueShouldSuccessWhenNoSetAddress() {
        assertThat(FinalRecipient.builder()
            .finalRecipient(Text.fromRawText("Plop\nGlark"))
            .build()
            .fieldValue())
            .isEqualTo("rfc822; Plop\r\n Glark");
    }

    @Test
    void fieldValueShouldSuccessWithFullProperties() {
        assertThat(FinalRecipient.builder()
            .finalRecipient(Text.fromRawText("Plop\nGlark"))
            .addressType(new AddressType("address type 1"))
            .build()
            .fieldValue())
            .isEqualTo("address type 1; Plop\r\n Glark");
    }
}
