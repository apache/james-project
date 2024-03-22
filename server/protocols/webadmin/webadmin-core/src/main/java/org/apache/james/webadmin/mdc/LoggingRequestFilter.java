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

package org.apache.james.webadmin.mdc;

import static org.apache.james.webadmin.authentication.AuthenticationFilter.LOGIN;

import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.util.MDCStructuredLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

import spark.Filter;
import spark.Request;
import spark.Response;

public class LoggingRequestFilter implements Filter {
    private static class DefaultRequestLogger implements RequestLogger {
        private static final DefaultRequestLogger INSTANCE = new DefaultRequestLogger();

        @Override
        public boolean applies(Request request) {
            return true;
        }

        @Override
        public void log(Request request, RequestId requestId) {
            MDCStructuredLogger.forLogger(LOGGER)
                    .field(REQUEST_ID, requestId.asString())
                    .field(IP, request.ip())
                    .field("real-ip", Optional.ofNullable(request.headers("X-Real-IP")).orElse(""))
                    .field(ENDPOINT, request.url())
                    .field(METHOD, request.requestMethod())
                    .field(LOGIN, request.attribute(LOGIN))
                    .field(QUERY_PARAMETERS, ImmutableSet.copyOf(request.queryParams()).toString())
                    .field(REQUEST_BODY, request.body())
                    .log(logger -> logger.info("WebAdmin request received"));
        }
    }

    public static final Logger LOGGER = LoggerFactory.getLogger(LoggingRequestFilter.class);
    public static final String REQUEST_BODY = "request-body";
    public static final String METHOD = "method";
    public static final String ENDPOINT = "endpoint";
    public static final String QUERY_PARAMETERS = "queryParameters";
    public static final String IP = "ip";
    public static final String REQUEST_ID = "requestId";

    public static LoggingRequestFilter create() {
        return new LoggingRequestFilter(ImmutableSet.of());
    }

    private final Set<RequestLogger> requestLoggers;

    @Inject
    public LoggingRequestFilter(Set<RequestLogger> requestLoggers) {
        this.requestLoggers = requestLoggers;
    }

    @Override
    public void handle(Request request, Response response) {
        RequestId requestId = RequestId.random();

        request.attribute(REQUEST_ID, requestId);

        requestLoggers.stream()
                .filter(requestLogger -> requestLogger.applies(request))
                .findFirst()
                .orElse(DefaultRequestLogger.INSTANCE)
                .log(request, requestId);
    }
}
