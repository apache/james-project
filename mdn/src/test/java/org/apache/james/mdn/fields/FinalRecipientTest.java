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

public class FinalRecipientTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldMatchBeanContract() throws Exception {
        EqualsVerifier.forClass(FinalRecipient.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    public void shouldThrowOnNullAddress() {
        expectedException.expect(NullPointerException.class);

        new FinalRecipient(null);
    }

    @Test
    public void shouldThrowOnNullAddressWithType() {
        expectedException.expect(NullPointerException.class);

        new FinalRecipient("customType", null);
    }

    @Test
    public void shouldThrowOnNullType() {
        expectedException.expect(NullPointerException.class);

        String addressType = null;
        new FinalRecipient(addressType, Text.fromRawText("address"));
    }

    @Test
    public void shouldThrowOnMultilineType() {
        expectedException.expect(IllegalArgumentException.class);

        String addressType = "a\nb";
        new FinalRecipient(addressType, Text.fromRawText("address"));
    }

    @Test
    public void typeShouldDefaultToRfc822() {
        Text address = Text.fromRawText("address");
        assertThat(new FinalRecipient(address))
            .isEqualTo(new FinalRecipient(Constants.RFC_822, address));
    }

    @Test
    public void formattedValueShouldDisplayAddress() {
        assertThat(new FinalRecipient(Text.fromRawText("Plop"))
            .formattedValue())
            .isEqualTo("Final-Recipient: rfc822; Plop");
    }

    @Test
    public void formattedValueShouldDisplayCustomType() {
        assertThat(new FinalRecipient("postal", Text.fromRawText("Plop"))
            .formattedValue())
            .isEqualTo("Final-Recipient: postal; Plop");
    }

    @Test
    public void formattedValueShouldDisplayMultilineAddress() {
        assertThat(new FinalRecipient(Text.fromRawText("Plop\nGlark"))
            .formattedValue())
            .isEqualTo("Final-Recipient: rfc822; Plop\r\n" +
                " Glark");
    }
}
