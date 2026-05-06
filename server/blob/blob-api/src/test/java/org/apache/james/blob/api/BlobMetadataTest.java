/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.blob.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class BlobMetadataTest {
    private static final String ONE_HUNDRED_TWENTY_SEVEN_CHARS_METADATA_NAME = "a".repeat(127);
    private static final String ONE_HUNDRED_TWENTY_EIGHT_CHARS_METADATA_NAME = "a".repeat(128);

    @Test
    void blobMetadataNameShouldBeCaseInsensitive() {
        assertThat(new BlobStoreDAO.BlobMetadataName("X-Test").name())
            .isEqualTo("x-test");
        assertThat(new BlobStoreDAO.BlobMetadataName("X-Test"))
            .isEqualTo(new BlobStoreDAO.BlobMetadataName("x-test"));
    }

    @ParameterizedTest
    @CsvSource({
        "metadata, metadata",
        "CONTENT-ENCODING, content-encoding",
        "x-test-123, x-test-123",
        "A1-B2-C3, a1-b2-c3"
    })
    void blobMetadataNameShouldAcceptLettersDigitsAndDash(String rawName, String expectedName) {
        assertThat(new BlobStoreDAO.BlobMetadataName(rawName).name())
            .isEqualTo(expectedName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"metadata_name", "metadata.name", "metadata name", "metadata/name", "metadata:name", "metadata#name"})
    void blobMetadataNameShouldRejectUnsupportedCharacters(String rawName) {
        assertThatThrownBy(() -> new BlobStoreDAO.BlobMetadataName(rawName))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blobMetadataNameShouldRejectEmptyName() {
        assertThatThrownBy(() -> new BlobStoreDAO.BlobMetadataName(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blobMetadataNameShouldAcceptNameBelowOneHundredTwentyEightCharacters() {
        assertThat(new BlobStoreDAO.BlobMetadataName(ONE_HUNDRED_TWENTY_SEVEN_CHARS_METADATA_NAME).name())
            .isEqualTo(ONE_HUNDRED_TWENTY_SEVEN_CHARS_METADATA_NAME);
    }

    @Test
    void blobMetadataNameShouldRejectNameOfOneHundredTwentyEightCharacters() {
        assertThatThrownBy(() -> new BlobStoreDAO.BlobMetadataName(ONE_HUNDRED_TWENTY_EIGHT_CHARS_METADATA_NAME))
            .isInstanceOf(IllegalArgumentException.class);
    }
}