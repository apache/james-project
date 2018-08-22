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
import org.apache.james.webadmin.utils.JsonExtractor;

import spark.Service;

public class ErrorRoutes implements Routes {

    public static final String BASE_URL = "/errors/";
    public static final String INTERNAL_SERVER_ERROR = "internalServerError";
    public static final String JSON_EXTRACT_EXCEPTION = "jsonExtractException";

    @Override
    public void define(Service service) {
        defineInternalError(service);
        defineJsonExtractException(service);
    }

    @Override
    public String getBasePath() {
        return BASE_URL;
    }

    private void defineInternalError(Service service) {
        service.get(BASE_URL + INTERNAL_SERVER_ERROR,
            (req, res) -> {
                throw new RuntimeException();
            });
    }

    private void defineJsonExtractException(Service service) {
        service.get(BASE_URL + JSON_EXTRACT_EXCEPTION,
            (req, res) -> new JsonExtractor<>(Long.class).parse("a non valid JSON"));
    }
}
