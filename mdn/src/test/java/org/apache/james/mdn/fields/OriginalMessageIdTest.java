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

class OriginalMessageIdTest {

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(OriginalMessageId.class)
            .verify();
    }

    @Test
    void shouldThrowOnNullMessageId() {
        assertThatThrownBy(() -> new OriginalMessageId(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowOnMultiLineMessageId() {
        assertThatThrownBy(() -> new OriginalMessageId("message\nid"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorShouldThrowOnEmpty() {
        assertThatThrownBy(() -> new OriginalMessageId(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorShouldThrowOnFoldingWhiteSpaces() {
        assertThatThrownBy(() -> new OriginalMessageId("   "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowOnNameWithLineBreakAtTheEnd() {
        String userAgentName = "a\n";

        assertThatThrownBy(() -> new OriginalMessageId(userAgentName))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowOnNameWithLineBreakAtTheBeginning() {
        String userAgentName = "\nb";

        assertThatThrownBy(() -> new OriginalMessageId(userAgentName))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formattedValueShouldDisplayMessageId() {
        assertThat(new OriginalMessageId("msgId").formattedValue())
            .isEqualTo("Original-Message-ID: msgId");
    }

    @Test
    void messageIdShouldBeTrimmed() {
        assertThat(new OriginalMessageId(" msgId ").getOriginalMessageId())
            .isEqualTo("msgId");
    }
}
