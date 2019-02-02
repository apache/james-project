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

package org.apache.james.webadmin.swagger.routes;

import javax.inject.Inject;

import org.apache.james.webadmin.PublicRoutes;
import org.apache.james.webadmin.WebAdminConfiguration;
import org.apache.james.webadmin.swagger.SwaggerParser;
import org.eclipse.jetty.http.HttpStatus;

import spark.Service;

public class SwaggerRoutes implements PublicRoutes {
    public static final String SWAGGER_ENDPOINT = "/james-swagger";
    private static final String APP_PACKAGE = "org.apache.james.webadmin.routes";
    private final WebAdminConfiguration webAdminConfiguration;

    @Inject
    public SwaggerRoutes(WebAdminConfiguration webAdminConfiguration) {
        this.webAdminConfiguration = webAdminConfiguration;
    }

    @Override
    public String getBasePath() {
        return SWAGGER_ENDPOINT;
    }

    @Override
    public void define(Service service) {
        service.get(SWAGGER_ENDPOINT, (request, response) -> {
            response.status(HttpStatus.OK_200);
            return SwaggerParser.getSwaggerJson(APP_PACKAGE, webAdminConfiguration);
        });
    }
}
