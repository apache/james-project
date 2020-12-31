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

package org.apache.james.webadmin.authentication;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.apache.james.jwt.JwtTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

import spark.HaltException;
import spark.Request;
import spark.Response;

class JwtFilterTest {
    private JwtTokenVerifier jwtTokenVerifier;
    private JwtFilter jwtFilter;

    @BeforeEach
    void setUp() {
        jwtTokenVerifier = mock(JwtTokenVerifier.class);
        jwtFilter = new JwtFilter(() -> jwtTokenVerifier);
    }

    @Test
    void handleShouldDoNothingOnOptions() throws Exception {
        Request request = mock(Request.class);
        //Ensure we don't take OPTIONS string from the constant pool
        when(request.requestMethod()).thenReturn(new String("OPTIONS"));
        Response response = mock(Response.class);

        jwtFilter.handle(request, response);

        verifyZeroInteractions(response);
    }


    @Test
    void handleShouldRejectRequestWithHeaders() throws Exception {
        Request request = mock(Request.class);
        when(request.requestMethod()).thenReturn("GET");
        when(request.headers()).thenReturn(ImmutableSet.of());

        assertThatThrownBy(() -> jwtFilter.handle(request, mock(Response.class)))
            .isInstanceOf(HaltException.class)
            .extracting(e -> HaltException.class.cast(e).statusCode())
            .isEqualTo(401);
    }

    @Test
    void handleShouldRejectRequestWithBearersHeaders() throws Exception {
        Request request = mock(Request.class);
        when(request.requestMethod()).thenReturn("GET");
        when(request.headers(JwtFilter.AUTHORIZATION_HEADER_NAME)).thenReturn("Invalid value");

        assertThatThrownBy(() -> jwtFilter.handle(request, mock(Response.class)))
            .isInstanceOf(HaltException.class)
            .extracting(e -> HaltException.class.cast(e).statusCode())
            .isEqualTo(401);
    }

    @Test
    void handleShouldRejectRequestWithInvalidBearerHeaders() throws Exception {
        Request request = mock(Request.class);
        when(request.requestMethod()).thenReturn("GET");
        when(request.headers(JwtFilter.AUTHORIZATION_HEADER_NAME)).thenReturn("Bearer value");
        when(jwtTokenVerifier.verify("value")).thenReturn(false);


        assertThatThrownBy(() -> jwtFilter.handle(request, mock(Response.class)))
            .isInstanceOf(HaltException.class)
            .extracting(e -> HaltException.class.cast(e).statusCode())
            .isEqualTo(401);
    }

    @Test
    void handleShouldRejectRequestWithoutAdminClaim() throws Exception {
        Request request = mock(Request.class);
        when(request.requestMethod()).thenReturn("GET");
        when(request.headers(JwtFilter.AUTHORIZATION_HEADER_NAME)).thenReturn("Bearer value");
        when(jwtTokenVerifier.verify("value")).thenReturn(true);
        when(jwtTokenVerifier.hasAttribute("admin", true, "value")).thenReturn(false);

        assertThatThrownBy(() -> jwtFilter.handle(request, mock(Response.class)))
            .isInstanceOf(HaltException.class)
            .extracting(e -> HaltException.class.cast(e).statusCode())
            .isEqualTo(401);
    }

    @Test
    void handleShouldAcceptValidJwt() throws Exception {
        Request request = mock(Request.class);
        when(request.requestMethod()).thenReturn("GET");
        when(request.headers(JwtFilter.AUTHORIZATION_HEADER_NAME)).thenReturn("Bearer value");
        when(jwtTokenVerifier.verify("value")).thenReturn(true);
        when(jwtTokenVerifier.hasAttribute("admin", true, "value")).thenReturn(true);

        jwtFilter.handle(request, mock(Response.class));
    }
}
