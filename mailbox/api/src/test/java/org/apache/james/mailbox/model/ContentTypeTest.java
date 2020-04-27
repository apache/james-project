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

package org.apache.james.mailbox.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class ContentTypeTest {
    @Test
    void contentTypeShouldRespectBeanContract() {
        EqualsVerifier.forClass(ContentType.class)
            .verify();
    }

    @Test
    void subTypeShouldRespectBeanContract() {
        EqualsVerifier.forClass(ContentType.SubType.class)
            .verify();
    }

    @Test
    void mediaTypeShouldRespectBeanContract() {
        EqualsVerifier.forClass(ContentType.MediaType.class)
            .verify();
    }

    @Test
    void mimeTypeShouldRespectBeanContract() {
        EqualsVerifier.forClass(ContentType.MimeType.class)
            .verify();
    }

    @Test
    void mimeTypeOfShouldThrowWhenInvalid() {
        assertThatThrownBy(() -> ContentType.MimeType.of("aaa"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mimeTypeOfShouldThrowWhenEmpty() {
        assertThatThrownBy(() -> ContentType.of(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mimeTypeOfShouldThrowWhenNull() {
        assertThatThrownBy(() -> ContentType.of((String) null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mimeTypeOfShouldReturnExpectedValue() {
        assertThat(ContentType.MimeType.of("text/html"))
            .isEqualTo(ContentType.MimeType.of(
                ContentType.MediaType.of("text"),
                ContentType.SubType.of("html")));
    }

    @Test
    void mediaTypeOfShouldThrowWhenEmpty() {
        assertThatThrownBy(() -> ContentType.MediaType.of(""))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void subTypeOfShouldThrowWhenEmpty() {
        assertThatThrownBy(() -> ContentType.SubType.of(""))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void asMime4JShouldNotThrowWhenInvalidContentType() {
        ContentType invalid = ContentType.of("/invalid");
        assertThatCode(invalid::asMime4J)
            .doesNotThrowAnyException();
    }

    @Test
    void mimeTypeShouldThrowWhenInvalidContentType() {
        ContentType invalid = ContentType.of("invalid");
        assertThatThrownBy(invalid::mimeType)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void mediaTypeShouldThrowWhenInvalidContentType() {
        ContentType invalid = ContentType.of("invalid");
        assertThatThrownBy(invalid::mediaType)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void subTypeShouldThrowWhenInvalidContentType() {
        ContentType invalid = ContentType.of("invalid");
        assertThatThrownBy(invalid::subType)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void charsetShouldThrowWhenInvalidCharset() {
        ContentType invalid = ContentType.of("text/plain; charset=invalid");
        assertThatThrownBy(invalid::charset)
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void charsetShouldThrowWhenEmptyCharset() {
        ContentType contentType = ContentType.of("text/plain; charset=");

        assertThat(contentType.charset())
            .isEmpty();
    }

    @Test
    void asStringShouldReturnWhenInvalid() {
        String value = "invalid";
        ContentType invalid = ContentType.of(value);
        assertThat(invalid.asString()).isEqualTo(value);
    }

    @Test
    void charsetShouldReturnEmptyWhenNone() {
        ContentType contentType = ContentType.of("text/html");

        assertThat(contentType.charset()).isEmpty();
    }

    @Test
    void charsetShouldReturnSuppliedCharset() {
        ContentType contentType = ContentType.of("text/html; charset=UTF-8");

        assertThat(contentType.charset()).contains(StandardCharsets.UTF_8);
    }

    @Test
    void mimeTypeShouldReturnSuppliedValue() {
        ContentType contentType = ContentType.of("text/html");

        assertThat(contentType.mimeType()).isEqualTo(ContentType.MimeType.of("text/html"));
    }

    @Test
    void subTypeShouldReturnSuppliedValue() {
        ContentType contentType = ContentType.of("text/html");

        assertThat(contentType.subType()).isEqualTo(ContentType.SubType.of("html"));
    }

    @Test
    void mediaTypeShouldReturnSuppliedValue() {
        ContentType contentType = ContentType.of("text/html");

        assertThat(contentType.mediaType()).isEqualTo(ContentType.MediaType.of("text"));
    }
}