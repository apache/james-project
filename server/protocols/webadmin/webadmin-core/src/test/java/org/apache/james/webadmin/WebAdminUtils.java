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
import java.time.Duration;
import java.util.List;

import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.util.Port;
import org.apache.james.webadmin.authentication.AuthenticationFilter;
import org.apache.james.webadmin.authentication.NoAuthenticationFilter;

import com.google.common.collect.ImmutableList;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import reactor.core.publisher.Mono;

public class WebAdminUtils {
    private static class ConcurrentSafeWebAdminServer extends WebAdminServer {
        ConcurrentSafeWebAdminServer(WebAdminConfiguration configuration, List<Routes> routesList, AuthenticationFilter authenticationFilter, MetricFactory metricFactory) {
            super(configuration, routesList, authenticationFilter, metricFactory);
        }

        /**
         * JVM-wide synchronized start method to avoid the all too common random port allocation conflict
         * that occurs when parallelly testing webadmin maven modules.
         */
        @Override
        public WebAdminServer start() {
            Mono.fromRunnable(super::start)
                .retryBackoff(5, Duration.ofMillis(10))
                .block();
            return this;
        }
    }

    public static WebAdminServer createWebAdminServer(Routes... routes) {
        return new ConcurrentSafeWebAdminServer(WebAdminConfiguration.TEST_CONFIGURATION,
            ImmutableList.copyOf(routes),
            new NoAuthenticationFilter(),
            new RecordingMetricFactory());
    }

    public static RequestSpecBuilder buildRequestSpecification(WebAdminServer webAdminServer) {
        return buildRequestSpecification(webAdminServer.getPort());
    }

    public static RequestSpecBuilder buildRequestSpecification(Port port) {
        return new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setConfig(defaultConfig())
            .setPort(port.getValue())
            .setBasePath("/");
    }

    public static RestAssuredConfig defaultConfig() {
        return newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8));
    }

    public static RequestSpecification spec(Port port) {
        return given().spec(buildRequestSpecification(port).build());
    }
}
