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

package org.apache.james.webadmin.routes;

import static org.apache.james.webadmin.authentication.AuthenticationFilter.LOGIN;
import static org.apache.james.webadmin.mdc.LoggingRequestFilter.ENDPOINT;
import static org.apache.james.webadmin.mdc.LoggingRequestFilter.IP;
import static org.apache.james.webadmin.mdc.LoggingRequestFilter.LOGGER;
import static org.apache.james.webadmin.mdc.LoggingRequestFilter.METHOD;
import static org.apache.james.webadmin.mdc.LoggingRequestFilter.QUERY_PARAMETERS;
import static org.apache.james.webadmin.mdc.LoggingRequestFilter.REQUEST_ID;

import org.apache.james.util.MDCStructuredLogger;
import org.apache.james.webadmin.mdc.RequestId;
import org.apache.james.webadmin.mdc.RequestLogger;

import com.google.common.collect.ImmutableSet;

import spark.Request;

// This class skips logging of the body for user creation requests as it contains the user password
public class UserCreationRequestLogger implements RequestLogger {
    @Override
    public boolean applies(Request request) {
        return request.pathInfo().startsWith(UserRoutes.USERS)
            && request.requestMethod().equals("PUT");
    }

    @Override
    public void log(Request request, RequestId requestId) {
        MDCStructuredLogger.forLogger(LOGGER)
                .addField(REQUEST_ID, requestId.asString())
                .addField(IP, request.ip())
                .addField(ENDPOINT, request.url())
                .addField(METHOD, request.requestMethod())
                .addField(LOGIN, request.attribute(LOGIN))
                .addField(QUERY_PARAMETERS, ImmutableSet.copyOf(request.queryParams()))
                .log(logger -> logger.info("WebAdmin request received: user creation request"));
    }
}
