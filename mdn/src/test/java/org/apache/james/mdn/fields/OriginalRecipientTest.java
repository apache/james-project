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

public class OriginalRecipientTest {
    public static final Text ADDRESS = Text.fromRawText("address");

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldMatchBeanContract() throws Exception {
        EqualsVerifier.forClass(OriginalRecipient.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    public void shouldThrowOnNullAddress() {
        expectedException.expect(NullPointerException.class);

        OriginalRecipient.builder().originalRecipient(null).build();
    }

    @Test
    public void shouldThrowOnNullAddressWhenCustomType() {
        expectedException.expect(NullPointerException.class);

        OriginalRecipient.builder()
            .addressType(new AddressType("customType"))
            .originalRecipient(null)
            .build();
    }

    @Test
    public void shouldThrowOnNullAddressType() {
        expectedException.expect(NullPointerException.class);

        OriginalRecipient.builder()
            .addressType(null)
            .originalRecipient(ADDRESS)
            .build();
    }

    @Test
    public void addressTypeShouldDefaultToRfc822() {
        assertThat(OriginalRecipient.builder().originalRecipient(ADDRESS).build())
            .isEqualTo(OriginalRecipient.builder().addressType(AddressType.RFC_822).originalRecipient(ADDRESS).build());
    }

    @Test
    public void formattedValueShouldDisplayAddress() {
        assertThat(OriginalRecipient.builder().originalRecipient(ADDRESS).build()
            .formattedValue())
            .isEqualTo("Original-Recipient: rfc822; address");
    }

    @Test
    public void formattedValueShouldDisplayCustomType() {
        assertThat(OriginalRecipient.builder().addressType(new AddressType("custom")).originalRecipient(ADDRESS).build()
            .formattedValue())
            .isEqualTo("Original-Recipient: custom; address");
    }

    @Test
    public void formattedValueShouldDisplayMultilineAddress() {
        assertThat(OriginalRecipient.builder().originalRecipient(Text.fromRawText("multiline\naddress")).build()
            .formattedValue())
            .isEqualTo("Original-Recipient: rfc822; multiline\r\n" +
                " address");
    }
}
