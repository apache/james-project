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

package org.apache.james.webadmin;

import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.util.Port;
import org.apache.james.webadmin.authentication.NoAuthenticationFilter;

import com.github.steveash.guavate.Guavate;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

public class WebAdminUtils {

    public static WebAdminServer createWebAdminServer(Routes... routes) {
        return new WebAdminServer(WebAdminConfiguration.TEST_CONFIGURATION,
            privateRoutes(routes),
            publicRoutes(routes),
            new NoAuthenticationFilter(),
            new NoopMetricFactory());
    }

    private static Set<Routes> privateRoutes(Routes[] routes) {
        return Arrays.stream(routes)
                .filter(route -> !(route instanceof PublicRoutes))
                .collect(Guavate.toImmutableSet());
    }

    private static Set<PublicRoutes> publicRoutes(Routes[] routes) {
        return Arrays.stream(routes)
                .filter(PublicRoutes.class::isInstance)
                .map(PublicRoutes.class::cast)
                .collect(Guavate.toImmutableSet());
    }

    public static RequestSpecBuilder buildRequestSpecification(WebAdminServer webAdminServer) {
        return buildRequestSpecification(webAdminServer.getPort());
    }

    public static RequestSpecBuilder buildRequestSpecification(Port port) {
        return new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setConfig(defaultConfig())
            .setPort(port.getValue());
    }

    public static RestAssuredConfig defaultConfig() {
        return newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8));
    }

    public static RequestSpecification spec(Port port) {
        return given().spec(buildRequestSpecification(port).build());
    }
}
