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

public class OriginalMessageIdTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldMatchBeanContract() throws Exception {
        EqualsVerifier.forClass(OriginalMessageId.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    public void shouldThrowOnNullMessageId() {
        expectedException.expect(NullPointerException.class);

        new OriginalMessageId(null);
    }

    @Test
    public void shouldThrowOnMultiLineMessageId() {
        expectedException.expect(IllegalArgumentException.class);

        new OriginalMessageId("message\nid");
    }

    @Test
    public void constructorShouldThrowOnEmpty() {
        expectedException.expect(IllegalArgumentException.class);

        new OriginalMessageId("");
    }

    @Test
    public void constructorShouldThrowOnFoldingWhiteSpaces() {
        expectedException.expect(IllegalArgumentException.class);

        new OriginalMessageId("   ");
    }

    @Test
    public void shouldThrowOnNameWithLineBreakAtTheEnd() {
        expectedException.expect(IllegalArgumentException.class);

        String userAgentName = "a\n";
        new OriginalMessageId(userAgentName);
    }

    @Test
    public void shouldThrowOnNameWithLineBreakAtTheBeginning() {
        expectedException.expect(IllegalArgumentException.class);

        String userAgentName = "\nb";
        new OriginalMessageId(userAgentName);
    }

    @Test
    public void formattedValueShouldDisplayMessageId() {
        assertThat(new OriginalMessageId("msgId")
            .formattedValue())
            .isEqualTo("Original-Message-ID: msgId");
    }

    @Test
    public void messageIdShouldBeTrimmed() {
        assertThat(new OriginalMessageId(" msgId ")
            .getOriginalMessageId())
            .isEqualTo("msgId");
    }
}
