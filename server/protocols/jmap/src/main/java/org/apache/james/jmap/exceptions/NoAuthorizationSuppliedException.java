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

package org.apache.james.jmap.exceptions;

import org.apache.james.jmap.http.AuthenticateHeader;

import reactor.netty.http.server.HttpServerResponse;

public class NoAuthorizationSuppliedException extends UnauthorizedException {
    private final AuthenticateHeader authenticateHeader;

    public NoAuthorizationSuppliedException(AuthenticateHeader authenticateHeader) {
        super("No valid authentication methods provided");
        this.authenticateHeader = authenticateHeader;
    }

    @Override
    public HttpServerResponse addHeaders(HttpServerResponse response) {
        return response.addHeader(AuthenticateHeader.HEADER_NAME, authenticateHeader.asHeaderValue());
    }
}
