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

package org.apache.james.webadmin.dropwizard;

import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.codahale.metrics.MetricRegistry;

import io.restassured.RestAssured;

class MetricsRoutesTest {
    WebAdminServer webAdminServer;
    MetricRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new MetricRegistry();
        webAdminServer = WebAdminUtils.createWebAdminServer(new MetricsRoutes(registry))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    void getShouldReturnSeveralMetric() {
        registry.counter("easy").inc();
        registry.counter("hard").inc();
        registry.counter("hard").inc();

        String body = when()
            .get("/metrics")
        .then()
            .statusCode(HttpStatus.OK_200)
            .extract()
            .body()
            .asString();

        assertThat(body)
            .contains(
                    "# HELP hard Generated from Dropwizard metric import (metric=hard, type=com.codahale.metrics.Counter)\n" +
                    "# TYPE hard gauge\n" +
                    "hard 2.0\n" +
                    "# HELP easy Generated from Dropwizard metric import (metric=easy, type=com.codahale.metrics.Counter)\n" +
                    "# TYPE easy gauge\n" +
                    "easy 1.0");
    }
}