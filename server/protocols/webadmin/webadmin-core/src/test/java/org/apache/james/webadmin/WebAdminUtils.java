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
import org.apache.james.webadmin.mdc.LoggingRequestFilter;

import com.google.common.collect.ImmutableList;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

public class WebAdminUtils {
    public interface Startable {
        WebAdminServer start() ;
    }

    private static class ConcurrentSafeWebAdminServerFactory {

        private final WebAdminConfiguration configuration;
        private final List<Routes> privateRoutes;
        private final AuthenticationFilter authenticationFilter;
        private final MetricFactory metricFactory;

        private ConcurrentSafeWebAdminServerFactory(WebAdminConfiguration configuration, List<Routes> privateRoutes, AuthenticationFilter authenticationFilter, MetricFactory metricFactory) {
            this.configuration = configuration;
            this.privateRoutes = privateRoutes;
            this.authenticationFilter = authenticationFilter;
            this.metricFactory = metricFactory;
        }

        /**
         * JVM-wide synchronized start method to avoid the all too common random port allocation conflict
         * that occurs when parallelly testing webadmin maven modules.
         */
        public Startable createServer() {
            return () -> Mono.fromCallable(this::createServerSingleTry)
                .retryWhen(Retry.backoff(10, Duration.ofMillis(10))
                    .scheduler(Schedulers.boundedElastic()))
                .block();
        }

        public WebAdminServer createServerSingleTry() {
            WebAdminServer webAdminServer = new WebAdminServer(configuration, privateRoutes, authenticationFilter, metricFactory, LoggingRequestFilter.create());
            try {
                return webAdminServer
                    .start();
            } catch (Exception e) {
                webAdminServer.destroy();
                throw e;
            }
        }
    }

    public static Startable createWebAdminServer(Routes... routes) {
        return new ConcurrentSafeWebAdminServerFactory(
                WebAdminConfiguration.TEST_CONFIGURATION,
                ImmutableList.copyOf(routes),
                new NoAuthenticationFilter(),
                new RecordingMetricFactory())
            .createServer();
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
        return given().spec(buildRequestSpecification(port).build())
            .urlEncodingEnabled(false); // no further automatically encoding by Rest Assured client. rf: https://issues.apache.org/jira/projects/JAMES/issues/JAMES-3936
    }
}
