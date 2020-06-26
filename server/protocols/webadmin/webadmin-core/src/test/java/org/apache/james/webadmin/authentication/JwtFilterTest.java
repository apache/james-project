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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.apache.james.jwt.JwtTokenVerifier;
import org.eclipse.jetty.http.HttpStatus;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableSet;

import spark.HaltException;
import spark.Request;
import spark.Response;

public class JwtFilterTest {

    public static final Matcher<HaltException> STATUS_CODE_MATCHER_401 = new BaseMatcher<HaltException>() {
        @Override
        public boolean matches(Object o) {
            if (o instanceof HaltException) {
                HaltException haltException = (HaltException) o;
                return haltException.statusCode() == HttpStatus.UNAUTHORIZED_401;
            }
            return false;
        }

        @Override
        public void describeTo(Description description) {
            
        }
    };

    private JwtTokenVerifier jwtTokenVerifier;
    private JwtFilter jwtFilter;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() {
        jwtTokenVerifier = mock(JwtTokenVerifier.class);
        jwtFilter = new JwtFilter(jwtTokenVerifier);
    }

    @Test
    public void handleShouldDoNothingOnOptions() throws Exception {
        Request request = mock(Request.class);
        //Ensure we don't take OPTIONS string from the constant pool
        when(request.requestMethod()).thenReturn(new String("OPTIONS"));
        Response response = mock(Response.class);

        jwtFilter.handle(request, response);

        verifyZeroInteractions(response);
    }


    @Test
    public void handleShouldRejectRequestWithHeaders() throws Exception {
        Request request = mock(Request.class);
        when(request.requestMethod()).thenReturn("GET");
        when(request.headers()).thenReturn(ImmutableSet.of());

        expectedException.expect(HaltException.class);
        expectedException.expect(STATUS_CODE_MATCHER_401);

        jwtFilter.handle(request, mock(Response.class));
    }

    @Test
    public void handleShouldRejectRequestWithBearersHeaders() throws Exception {
        Request request = mock(Request.class);
        when(request.requestMethod()).thenReturn("GET");
        when(request.headers(JwtFilter.AUTHORIZATION_HEADER_NAME)).thenReturn("Invalid value");

        expectedException.expect(HaltException.class);
        expectedException.expect(STATUS_CODE_MATCHER_401);

        jwtFilter.handle(request, mock(Response.class));
    }

    @Test
    public void handleShouldRejectRequestWithInvalidBearerHeaders() throws Exception {
        Request request = mock(Request.class);
        when(request.requestMethod()).thenReturn("GET");
        when(request.headers(JwtFilter.AUTHORIZATION_HEADER_NAME)).thenReturn("Bearer value");
        when(jwtTokenVerifier.verify("value")).thenReturn(false);

        expectedException.expect(HaltException.class);
        expectedException.expect(STATUS_CODE_MATCHER_401);

        jwtFilter.handle(request, mock(Response.class));
    }

    @Test
    public void handleShouldRejectRequestWithoutAdminClaim() throws Exception {
        Request request = mock(Request.class);
        when(request.requestMethod()).thenReturn("GET");
        when(request.headers(JwtFilter.AUTHORIZATION_HEADER_NAME)).thenReturn("Bearer value");
        when(jwtTokenVerifier.verify("value")).thenReturn(true);
        when(jwtTokenVerifier.hasAttribute("admin", true, "value")).thenReturn(false);

        expectedException.expect(HaltException.class);
        expectedException.expect(STATUS_CODE_MATCHER_401);

        jwtFilter.handle(request, mock(Response.class));
    }

    @Test
    public void handleShouldAcceptValidJwt() throws Exception {
        Request request = mock(Request.class);
        when(request.requestMethod()).thenReturn("GET");
        when(request.headers(JwtFilter.AUTHORIZATION_HEADER_NAME)).thenReturn("Bearer value");
        when(jwtTokenVerifier.verify("value")).thenReturn(true);
        when(jwtTokenVerifier.hasAttribute("admin", true, "value")).thenReturn(true);

        jwtFilter.handle(request, mock(Response.class));
    }
}
