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

package org.apache.james.mock.smtp.server.model;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.mock.smtp.server.Fixture.OBJECT_MAPPER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.mock.smtp.server.model.Response.SMTPStatusCode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class ResponseTest {
    static final int OK_250_CODE = 250;
    static final Response.SMTPStatusCode OK_250 = Response.SMTPStatusCode.of(OK_250_CODE);

    @Nested
    class SMTPStatusCodeTest {
        @Test
        void shouldMatchBeanContract() {
            EqualsVerifier.forClass(Response.SMTPStatusCode.class)
                .verify();
        }

        @Test
        void constructorShouldThrowWhenStatusCodeIsNegative() {
            assertThatThrownBy(() -> Response.SMTPStatusCode.of(-1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void constructorShouldThrowWhenStatusCodeIsZero() {
            assertThatThrownBy(() -> Response.SMTPStatusCode.of(0))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void constructorShouldThrowWhenStatusCodeIsTooBig() {
            assertThatThrownBy(() -> Response.SMTPStatusCode.of(600))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void constructorShouldThrowWhenStatusCodeIsTooLittle() {
            assertThatThrownBy(() -> Response.SMTPStatusCode.of(99))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void getCodeShouldReturnInternalValue() {
            assertThat(OK_250.getRawCode())
                .isEqualTo(OK_250_CODE);
        }
    }

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(Response.class)
            .verify();
    }

    @Test
    void constructorShouldThrowWhenMessageIsNull() {
        assertThatThrownBy(() -> new Response(SMTPStatusCode.ACTION_COMPLETE_250, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorShouldThrowWhenCodeIsNull() {
        assertThatThrownBy(() -> new Response(null, "message"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void asReplyStringShouldReturnASMTPResponseLine() {
        assertThat(new Response(SMTPStatusCode.ACTION_COMPLETE_250, "message").asReplyString())
            .isEqualTo("250 message");
    }

    @Test
    void asReplyStringShouldReturnASMTPResponseLineWhenEmptyMessage() {
        assertThat(new Response(SMTPStatusCode.ACTION_COMPLETE_250, "").asReplyString())
            .isEqualTo("250 ");
    }

    @Nested
    class JSONTest {
        @Test
        void jacksonShouldDeserializeResponse() throws Exception {
            Response response = OBJECT_MAPPER.readValue(
                "{\"code\":250, \"message\":\"OK\"}",
                Response.class);

            assertThat(response).isEqualTo(new Response(Response.SMTPStatusCode.of(250), "OK"));
        }

        @Test
        void jacksonShouldSerializeResponse() throws Exception {
            String json = OBJECT_MAPPER.writeValueAsString(new Response(Response.SMTPStatusCode.of(250), "OK"));

            assertThatJson(json).isEqualTo("{\"code\":250, \"message\":\"OK\"}");
        }
    }
}