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

import org.apache.james.mdn.Constants;
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

        Text originalRecipient = null;
        new OriginalRecipient(originalRecipient);
    }

    @Test
    public void shouldThrowOnNullAddressWhenCustomType() {
        expectedException.expect(NullPointerException.class);

        Text originalRecipient = null;
        new OriginalRecipient("customType", originalRecipient);
    }

    @Test
    public void shouldThrowOnNullAddressType() {
        expectedException.expect(NullPointerException.class);

        String addressType = null;
        new OriginalRecipient(addressType, ADDRESS);
    }

    @Test
    public void addressTypeShouldDefaultToRfc822() {
        assertThat(new OriginalRecipient(ADDRESS))
            .isEqualTo(new OriginalRecipient(Constants.RFC_822, ADDRESS));
    }

    @Test
    public void formattedValueShouldDisplayAddress() {
        assertThat(new OriginalRecipient(ADDRESS)
            .formattedValue())
            .isEqualTo("Original-Recipient: rfc822; address");
    }

    @Test
    public void formattedValueShouldDisplayCustomType() {
        assertThat(new OriginalRecipient("custom", ADDRESS)
            .formattedValue())
            .isEqualTo("Original-Recipient: custom; address");
    }

    @Test
    public void formattedValueShouldDisplayMultilineAddress() {
        assertThat(new OriginalRecipient(Text.fromRawText("multiline\naddress"))
            .formattedValue())
            .isEqualTo("Original-Recipient: rfc822; multiline\r\n" +
                " address");
    }
}
