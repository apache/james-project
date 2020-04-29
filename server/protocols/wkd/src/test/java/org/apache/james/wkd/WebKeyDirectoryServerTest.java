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
package org.apache.james.wkd;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.wkd.WebKeyDirectoryConfiguration;
import org.apache.james.wkd.WebKeyDirectoryRoutes;
import org.apache.james.wkd.WebKeyDirectoryServer;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

class WebKeyDirectoryServerTest {
    private static final WebKeyDirectoryConfiguration DISABLED_CONFIGURATION = WebKeyDirectoryConfiguration
        .builder().disable().build();
    private static final WebKeyDirectoryConfiguration TEST_CONFIGURATION = WebKeyDirectoryConfiguration
        .builder().enable().randomPort().build();
    private static final ImmutableSet<WebKeyDirectoryRoutes> NO_ROUTES = ImmutableSet.of();

    @Test
    void serverShouldAnswerWhenStarted() {
        WebKeyDirectoryServer WebKeyDirectoryServer = new WebKeyDirectoryServer(TEST_CONFIGURATION,
            NO_ROUTES);
        WebKeyDirectoryServer.start();

        try {
            given().port(WebKeyDirectoryServer.getPort().getValue()).basePath("http://localhost")
                .when().get().then().statusCode(404);
        } finally {
            WebKeyDirectoryServer.stop();
        }
    }

    @Test
    void startShouldNotThrowWhenConfigurationDisabled() {
        WebKeyDirectoryServer WebKeyDirectoryServer = new WebKeyDirectoryServer(
            DISABLED_CONFIGURATION, NO_ROUTES);

        assertThatCode(WebKeyDirectoryServer::start).doesNotThrowAnyException();
    }

    @Test
    void stopShouldNotThrowWhenConfigurationDisabled() {
        WebKeyDirectoryServer WebKeyDirectoryServer = new WebKeyDirectoryServer(
            DISABLED_CONFIGURATION, NO_ROUTES);
        WebKeyDirectoryServer.start();

        assertThatCode(WebKeyDirectoryServer::stop).doesNotThrowAnyException();
    }

    @Test
    void getPortShouldThrowWhenServerIsNotStarted() {
        WebKeyDirectoryServer WebKeyDirectoryServer = new WebKeyDirectoryServer(TEST_CONFIGURATION,
            NO_ROUTES);

        assertThatThrownBy(WebKeyDirectoryServer::getPort)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getPortShouldThrowWhenDisabledConfiguration() {
        WebKeyDirectoryServer WebKeyDirectoryServer = new WebKeyDirectoryServer(
            DISABLED_CONFIGURATION, NO_ROUTES);
        WebKeyDirectoryServer.start();

        assertThatThrownBy(WebKeyDirectoryServer::getPort)
            .isInstanceOf(IllegalStateException.class);
    }
}
