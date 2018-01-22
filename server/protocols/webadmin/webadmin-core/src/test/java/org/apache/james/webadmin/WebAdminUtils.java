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

import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.webadmin.authentication.NoAuthenticationFilter;

import com.google.common.collect.ImmutableSet;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;

public class WebAdminUtils {

    public static WebAdminConfiguration webAdminConfigurationForTesting() {
        return WebAdminConfiguration.builder()
            .enabled()
            .port(new RandomPortSupplier())
            .build();
    }

    public static WebAdminServer createWebAdminServer(MetricFactory metricFactory, Routes... routes) throws IOException {
        return new WebAdminServer(webAdminConfigurationForTesting(),
            ImmutableSet.copyOf(routes),
            new NoAuthenticationFilter(),
            metricFactory);
    }

    public static RequestSpecBuilder defineRequestSpecification(WebAdminServer webAdminServer) {
        return new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .setPort(webAdminServer.getPort().get().getValue());
    }

}
