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

public class TextTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldMatchBeanContact() {
        EqualsVerifier.forClass(Text.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    public void fromRawTextShouldThrowOnNull() {
        expectedException.expect(NullPointerException.class);

        Text.fromRawText(null);
    }

    @Test
    public void formattedShouldRenderEmptyStrings() {
        Text text = Text.fromRawText("");

        assertThat(text.formatted()).isEqualTo("");
    }

    @Test
    public void formattedShouldKeepSpaces() {
        Text text = Text.fromRawText("text with spaces");

        assertThat(text.formatted()).isEqualTo("text with spaces");
    }

    @Test
    public void formattedShouldWrapLines() {
        Text text = Text.fromRawText("text with spaces\r\non several lines");

        assertThat(text.formatted()).isEqualTo("text with spaces\r\n on several lines");
    }

    @Test
    public void formattedShouldPreserveLineWrapping() {
        Text text = Text.fromRawText("text with spaces\r\n on several lines");

        assertThat(text.formatted()).isEqualTo("text with spaces\r\n on several lines");
    }

    @Test
    public void formattedShouldTrimExtraSpacesAfterWrapping() {
        Text text = Text.fromRawText("text with spaces\r\n  on several lines");

        assertThat(text.formatted()).isEqualTo("text with spaces\r\n on several lines");
    }

    @Test
    public void formattedShouldTrimExtraSpacesBeforeWrapping() {
        Text text = Text.fromRawText("text with spaces  \r\non several lines");

        assertThat(text.formatted()).isEqualTo("text with spaces\r\n on several lines");
    }

    @Test
    public void formattedShouldPreserveFoldingSpaces() {
        Text text = Text.fromRawText("text with folding    spaces");

        assertThat(text.formatted()).isEqualTo("text with folding    spaces");
    }
}
