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

import org.apache.james.webadmin.Routes;

import spark.Service;

public class CORSRoute implements Routes {
    // Fixed lists: echoing the request would grant any method or header, eg X-HTTP-Method-Override
    public static final String ALLOWED_METHODS = "GET, POST, PUT, PATCH, DELETE";
    public static final String ALLOWED_HEADERS = "Content-Type, Accept, Authorization, Password";

    @Override
    public String getBasePath() {
        return "";
    }

    @Override
    public void define(Service service) {
        service.options("/*", (request, response) -> {
            response.header("Access-Control-Allow-Methods", ALLOWED_METHODS);
            response.header("Access-Control-Allow-Headers", ALLOWED_HEADERS);
            return "";
        });
    }
}
