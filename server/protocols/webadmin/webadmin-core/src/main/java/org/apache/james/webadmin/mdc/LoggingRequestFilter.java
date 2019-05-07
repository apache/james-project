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

import org.apache.james.util.MDCStructuredLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

import spark.Filter;
import spark.Request;
import spark.Response;

public class LoggingRequestFilter implements Filter {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingRequestFilter.class);
    static final String REQUEST_BODY = "request-body";
    static final String METHOD = "method";
    static final String ENDPOINT = "endpoint";
    static final String QUERY_PARAMETERS = "queryParameters";
    static final String IP = "ip";
    static final String REQUEST_ID = "requestId";

    @Override
    public void handle(Request request, Response response) {
        RequestId requestId = RequestId.random();

        request.attribute(REQUEST_ID, requestId);

        MDCStructuredLogger.forLogger(LOGGER)
            .addField(REQUEST_ID, requestId.asString())
            .addField(IP, request.ip())
            .addField(ENDPOINT, request.url())
            .addField(METHOD, request.requestMethod())
            .addField(LOGIN, request.attribute(LOGIN))
            .addField(QUERY_PARAMETERS, ImmutableSet.copyOf(request.queryParams()))
            .addField(REQUEST_BODY, request.body())
            .log(logger -> logger.info("WebAdmin request received"));
    }
}
