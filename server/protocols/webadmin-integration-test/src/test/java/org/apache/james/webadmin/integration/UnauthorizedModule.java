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
package org.apache.james.webadmin.integration;

import static spark.Spark.halt;

import org.apache.james.webadmin.authentication.AuthenticationFilter;
import org.eclipse.jetty.http.HttpStatus;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

import spark.Request;
import spark.Response;

public class UnauthorizedModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(UnauthorizedModule.UnauthorizedFilter.class).in(Scopes.SINGLETON);
        bind(AuthenticationFilter.class).to(UnauthorizedModule.UnauthorizedFilter.class);
    }

    private static class UnauthorizedFilter implements AuthenticationFilter {

        @Override
        public void handle(Request request, Response response) throws Exception {
            halt(HttpStatus.UNAUTHORIZED_401, "Unauthorize every endpoints.");
        }
    }
}