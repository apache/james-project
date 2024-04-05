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

package org.apache.james.jmap.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.jmap.api.model.Preview;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class PreviewDTOTest {

    private static final String EMPTY_PREVIEW = "(Empty)";
    private static final String SAMPLE_PREVIEW_VALUE = "hello bob!";

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(PreviewDTO.class)
            .verify();
    }

    @Test
    void fromShouldReturnPreviewWithTheValue() {
        assertThat(PreviewDTO.from(Optional.of(Preview.from(SAMPLE_PREVIEW_VALUE))))
            .isEqualTo(PreviewDTO.of(SAMPLE_PREVIEW_VALUE));
    }

    @Test
    void fromShouldReturnNoBodyWhenNoPreview() {
        assertThat(PreviewDTO.from(Optional.empty()))
            .isEqualTo(PreviewDTO.of(EMPTY_PREVIEW));
    }

    @Test
    void fromShouldReturnNoBodyWhenPreviewOfEmptyString() {
        assertThat(PreviewDTO.from(Optional.of(Preview.from(""))))
            .isEqualTo(PreviewDTO.of(EMPTY_PREVIEW));
    }

    @Test
    void ofShouldReturnPreviewWithTheValue() {
        assertThat(PreviewDTO.of(SAMPLE_PREVIEW_VALUE))
            .isEqualTo(PreviewDTO.of(SAMPLE_PREVIEW_VALUE));
    }

    @Test
    void ofShouldReturnNoBodyWhenPreviewOfEmptyString() {
        assertThat(PreviewDTO.of(""))
            .isEqualTo(PreviewDTO.of(EMPTY_PREVIEW));
    }
}