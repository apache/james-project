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

package org.apache.james.jmap.api.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.google.common.base.Strings;

import nl.jqno.equalsverifier.EqualsVerifier;

class PreviewTest {

    private static final String PREVIEW_RAW_VALUE = "Hello James!";

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(Preview.class)
            .verify();
    }

    @Test
    void getValueShouldReturnCorrectPreviewString() {
        assertThat(new Preview(PREVIEW_RAW_VALUE).getValue())
            .isEqualTo(PREVIEW_RAW_VALUE);
    }

    @Test
    void fromShouldReturnACorrectPreview() {
        assertThat(Preview.from(PREVIEW_RAW_VALUE))
            .isEqualTo(new Preview(PREVIEW_RAW_VALUE));
    }

    @Test
    void fromShouldThrowWhenNullValue() {
        assertThatThrownBy(() -> Preview.from(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fromShouldThrowWhenValueLengthIsLongerThanMaximum256() {
        String errorMessageRegex = "the preview value '.*' has length longer than 256";
        assertThatThrownBy(() -> Preview.from(Strings.repeat("a", 257)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageMatching(errorMessageRegex);
    }

    @Test
    void fromShouldNotThrowWhenValueLengthIsEqualsToMaximum256() {
        assertThatCode(() -> Preview.from(Strings.repeat("a", 256)))
            .doesNotThrowAnyException();
    }
}