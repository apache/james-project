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

package org.apache.james.jmap;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

class JMAPServerTest {
    private static final JMAPConfiguration DISABLED_CONFIGURATION = JMAPConfiguration.builder().disable().build();
    private static final JMAPConfiguration TEST_CONFIGURATION = JMAPConfiguration.builder()
        .enable()
        .randomPort()
        .build();
    private static final ImmutableSet<JMAPRoutes> NO_ROUTES = ImmutableSet.of();

    @Test
    void serverShouldAnswerWhenStarted() {
        JMAPServer jmapServer = new JMAPServer(TEST_CONFIGURATION, NO_ROUTES);
        jmapServer.start();

        try {
            given()
                .port(jmapServer.getPort().getValue())
                .basePath("http://localhost")
            .when()
                .get()
            .then()
                .statusCode(404);
        } finally {
            jmapServer.stop();
        }
    }

    @Test
    void startShouldNotThrowWhenConfigurationDisabled() {
        JMAPServer jmapServer = new JMAPServer(DISABLED_CONFIGURATION, NO_ROUTES);

        assertThatCode(jmapServer::start).doesNotThrowAnyException();
    }

    @Test
    void stopShouldNotThrowWhenConfigurationDisabled() {
        JMAPServer jmapServer = new JMAPServer(DISABLED_CONFIGURATION, NO_ROUTES);
        jmapServer.start();

        assertThatCode(jmapServer::stop).doesNotThrowAnyException();
    }

    @Test
    void getPortShouldThrowWhenServerIsNotStarted() {
        JMAPServer jmapServer = new JMAPServer(TEST_CONFIGURATION, NO_ROUTES);

        assertThatThrownBy(jmapServer::getPort)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getPortShouldThrowWhenDisabledConfiguration() {
        JMAPServer jmapServer = new JMAPServer(DISABLED_CONFIGURATION, NO_ROUTES);
        jmapServer.start();

        assertThatThrownBy(jmapServer::getPort)
            .isInstanceOf(IllegalStateException.class);
    }
}
