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
package org.apache.james.jmap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.james.jmap.api.AccessTokenManager;
import org.apache.james.jmap.api.access.AccessToken;
import org.junit.Before;
import org.junit.Test;

public class AuthenticationFilterTest {
    private static final String TOKEN = "df991d2a-1c5a-4910-a90f-808b6eda133e";

    private HttpServletRequest mockedRequest;
    private HttpServletResponse mockedResponse;
    private AccessTokenManager mockedAccessTokenManager;
    private AuthenticationFilter tested;
    private FilterChain filterChain;
    
    @Before
    public void setup() throws Exception {
        mockedRequest = mock(HttpServletRequest.class);
        mockedResponse = mock(HttpServletResponse.class);
        mockedAccessTokenManager = mock(AccessTokenManager.class);
        tested = new AuthenticationFilter(mockedAccessTokenManager);
        filterChain = mock(FilterChain.class);
    }
    
    @Test
    public void filterShouldReturnUnauthorizedOnNullAuthorizationHeader() throws Exception {
        when(mockedRequest.getHeader("Authorization"))
            .thenReturn(null);
        
        tested.doFilter(mockedRequest, mockedResponse, filterChain);
        
        verify(mockedResponse).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
    
    @Test
    public void filterShouldReturnUnauthorizedOnInvalidAuthorizationHeader() throws Exception {
        when(mockedRequest.getHeader("Authorization"))
            .thenReturn(TOKEN);
        when(mockedAccessTokenManager.isValid(AccessToken.fromString(TOKEN)))
            .thenReturn(false);
        
        tested.doFilter(mockedRequest, mockedResponse, filterChain);
        
        verify(mockedResponse).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }
    
    @Test
    public void filterShouldChainOnValidAuthorizationHeader() throws Exception {
        when(mockedRequest.getHeader("Authorization"))
            .thenReturn(TOKEN);
        when(mockedAccessTokenManager.isValid(AccessToken.fromString(TOKEN)))
            .thenReturn(true);
        
        tested.doFilter(mockedRequest, mockedResponse, filterChain);
        
        verify(filterChain).doFilter(mockedRequest, mockedResponse);
    }
    
}
