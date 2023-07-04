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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HashBlobIdTest {

    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();

    @BeforeEach
    void beforeEach(){
        System.clearProperty("james.blob.id.hash.encoding");
    }

    @Test
    void shouldRespectBeanContract() {
        EqualsVerifier.forClass(HashBlobId.class).verify();
    }

    @Test
    void fromShouldConstructBlobId() {
        String id = "111";
        assertThat(BLOB_ID_FACTORY.from(id))
            .isEqualTo(new HashBlobId(id));
    }

    @Test
    void fromShouldThrowOnNull() {
        assertThatThrownBy(() -> BLOB_ID_FACTORY.from(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromShouldThrowOnEmpty() {
        assertThatThrownBy(() -> BLOB_ID_FACTORY.from(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void forPayloadShouldThrowOnNull() {
        assertThatThrownBy(() -> BLOB_ID_FACTORY.forPayload((byte[]) null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void forPayloadShouldHashEmptyArray() {
        BlobId blobId = BLOB_ID_FACTORY.forPayload(new byte[0]);

        assertThat(blobId.asString()).isEqualTo("47DEQpj8HBSa-_TImW-5JCeuQeRkm5NMpJWZG3hSuFU=");
    }

    @Test
    void forPayloadShouldHashArray() {
        BlobId blobId = BLOB_ID_FACTORY.forPayload("content".getBytes(StandardCharsets.UTF_8));

        assertThat(blobId.asString()).isEqualTo("7XACtDnprIRfIjV9giusFERzD722AW0-yUMil7nsn3M=");
    }


    @ParameterizedTest
    @MethodSource("encodingTypeAndExpectedHash")
    void forPayloadShouldSupportEncodingWhenConfigured(String encoding, String expectedHash) {
        System.setProperty("james.blob.id.hash.encoding", encoding);
        BlobId blobId = new HashBlobId.Factory().forPayload("content".getBytes(StandardCharsets.UTF_8));
        assertThat(blobId.asString()).isEqualTo(expectedHash);
    }

    static Stream<Arguments> encodingTypeAndExpectedHash() {
        return Stream.of(
                Arguments.of("base16", "ED7002B439E9AC845F22357D822BAC1444730FBDB6016D3EC9432297B9EC9F73"),
                Arguments.of("hex", "ED7002B439E9AC845F22357D822BAC1444730FBDB6016D3EC9432297B9EC9F73"),
                Arguments.of("base32", "5VYAFNBZ5GWIIXZCGV6YEK5MCRCHGD55WYAW2PWJIMRJPOPMT5ZQ===="),
                Arguments.of("base64", "7XACtDnprIRfIjV9giusFERzD722AW0+yUMil7nsn3M="),
                Arguments.of("base64Url", "7XACtDnprIRfIjV9giusFERzD722AW0-yUMil7nsn3M="),
                Arguments.of("base32", "5VYAFNBZ5GWIIXZCGV6YEK5MCRCHGD55WYAW2PWJIMRJPOPMT5ZQ===="),
                Arguments.of("base32Hex", "TLO05D1PT6M88NP26LUO4ATC2H2763TTMO0MQFM98CH9FEFCJTPG===="));
    }

    @Test
    void newFactoryShouldFailWhenInvalidEncoding() {
        System.setProperty("james.blob.id.hash.encoding", "invalid");
        assertThatThrownBy(HashBlobId.Factory::new)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown encoding type: invalid");
    }

    @Test
    void forPayloadShouldCalculateDifferentHashesWhenCraftedSha1Collision() throws Exception {
        byte[] payload1 = ClassLoaderUtils.getSystemResourceAsByteArray("shattered-1.pdf");
        byte[] payload2 = ClassLoaderUtils.getSystemResourceAsByteArray("shattered-2.pdf");
        BlobId blobId1 = BLOB_ID_FACTORY.forPayload(payload1);
        BlobId blobId2 = BLOB_ID_FACTORY.forPayload(payload2);
        assertThat(blobId1).isNotEqualTo(blobId2);
    }
}
