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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.apache.james.webadmin.authentication.NoAuthenticationFilter;
import org.apache.james.webadmin.mdc.LoggingRequestFilter;
import org.junit.jupiter.api.Test;

class WebAdminUtilsTest {

    @Test
    void serverShouldBeAbleToStartConcurrently() throws Exception {
        ConcurrentTestRunner.builder()
            .operation((a, b) -> {
                WebAdminServer webAdminServer = WebAdminUtils.createWebAdminServer()
                    .start();
                webAdminServer.destroy();
            })
            .threadCount(10)
            .operationCount(100)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
    }

    @Test
    void webAdminStartShouldNotHangUponPortConflict() {
        WebAdminServer successWebAdmin = WebAdminUtils.createWebAdminServer()
            .start();

        WebAdminConfiguration conflictedPortConfiguration = WebAdminConfiguration.builder()
            .enabled()
            .corsDisabled()
            .host("127.0.0.1")
            .port(successWebAdmin::getPort)
            .build();

        assertThatThrownBy(() -> new WebAdminServer(conflictedPortConfiguration, List.of(), new NoAuthenticationFilter(), new RecordingMetricFactory(), LoggingRequestFilter.create())
            .start())
            .rootCause()
            .isInstanceOf(TimeoutException.class);

        successWebAdmin.destroy();
    }
}