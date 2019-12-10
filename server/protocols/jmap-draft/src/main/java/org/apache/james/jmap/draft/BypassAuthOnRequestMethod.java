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

package org.apache.james.jmap.draft;

import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

public class BypassAuthOnRequestMethod implements Filter {

    public static Builder bypass(AuthenticationFilter authenticationFilter) {
        return new Builder(authenticationFilter);
    }

    public static class Builder {
        private final ImmutableList.Builder<Predicate<HttpServletRequest>> reasons = new ImmutableList.Builder<>();
        private final AuthenticationFilter authenticationFilter;

        private Builder(AuthenticationFilter authenticationFilter) {
            this.authenticationFilter = authenticationFilter;
        }

        public InitializedBuilder on(String requestMethod) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(requestMethod), "'requestMethod' is mandatory");
            String trimmedRequestMethod = requestMethod.trim();
            Preconditions.checkArgument(!Strings.isNullOrEmpty(trimmedRequestMethod), "'requestMethod' is mandatory");
            reasons.add(r -> r.getMethod().equalsIgnoreCase(trimmedRequestMethod));
            return new InitializedBuilder(this);
        }

        public static class InitializedBuilder {
            private final Builder builder;

            private InitializedBuilder(Builder builder) {
                this.builder = builder;
            }

            public InitializedBuilder and(String requestMethod) {
                return builder.on(requestMethod);
            }

            public BypassAuthOnRequestMethod only() {
                return new BypassAuthOnRequestMethod(builder.authenticationFilter, builder.reasons.build());
            }
        }
    }


    private final AuthenticationFilter authenticationFilter;
    private final List<Predicate<HttpServletRequest>> listOfReasonsToBypassAuth;

    private BypassAuthOnRequestMethod(AuthenticationFilter authenticationFilter, List<Predicate<HttpServletRequest>> listOfReasonsToBypassAuth) {
        this.authenticationFilter = authenticationFilter;
        this.listOfReasonsToBypassAuth = listOfReasonsToBypassAuth;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest)request;

        if (shouldBypassAuth(httpRequest)) {
            bypassAuth(request, response, chain);
        } else {
            tryAuth(httpRequest, response, chain);
        }
    }

    private boolean shouldBypassAuth(HttpServletRequest httpRequest) {
        return listOfReasonsToBypassAuth.stream()
                .anyMatch(r -> r.test(httpRequest));
    }

    @Override
    public void destroy() {
    }

    private void bypassAuth(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        chain.doFilter(request, response);
    }

    private void tryAuth(HttpServletRequest httpRequest, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        authenticationFilter.doFilter(httpRequest, response, chain);
    }

}
