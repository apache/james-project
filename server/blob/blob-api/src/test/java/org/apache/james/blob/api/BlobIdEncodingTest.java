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

package org.apache.james.blob.api;

import static org.apache.james.blob.api.BlobIdEncoding.ENCODING_PROPERTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.common.io.BaseEncoding;

class BlobIdEncodingTest {
    private static final byte[] PAYLOAD = "payload".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    @AfterEach
    void clearProperty() {
        System.clearProperty(ENCODING_PROPERTY);
    }

    @Test
    void blobIdFactoryCreationShouldFailOnInvalidProperty() {
        System.setProperty(ENCODING_PROPERTY, "blobIdFactoryCreationShouldFailOnInvalidProperty");

        assertThatThrownBy(PlainBlobId.Factory::new)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unknown encoding type: blobIdFactoryCreationShouldFailOnInvalidProperty");
    }

    @ParameterizedTest
    @ValueSource(strings = {"base16", "hex", "base32", "base32Hex", "base64", "base64Url"})
    void blobIdFactoryCreationShouldAcceptSupportedEncodings(String encoding) {
        System.setProperty(ENCODING_PROPERTY, encoding);

        assertThatCode(PlainBlobId.Factory::new).doesNotThrowAnyException();
    }

    @Test
    void shouldDefaultToBase64Url() {
        assertThat(BlobIdEncoding.fromSystemProperties().encode(PAYLOAD))
            .isEqualTo(BaseEncoding.base64Url().encode(PAYLOAD));
    }

    @Test
    void shouldHonourTheConfiguredEncoding() {
        System.setProperty(ENCODING_PROPERTY, "base16");

        assertThat(BlobIdEncoding.fromSystemProperties().encode(PAYLOAD))
            .isEqualTo(BaseEncoding.base16().encode(PAYLOAD));
    }

    @Test
    void hexShouldBeAnAliasOfBase16() {
        assertThat(BlobIdEncoding.baseEncodingFrom("hex"))
            .isEqualTo(BlobIdEncoding.baseEncodingFrom("base16"));
    }
}
