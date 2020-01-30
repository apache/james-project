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

package org.apache.james.mock.smtp.server.testing;

import org.apache.james.mock.smtp.server.ConfigurationClient;
import org.apache.james.util.Host;
import org.apache.james.util.docker.DockerContainer;
import org.apache.james.util.docker.Images;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockSmtpServerExtension implements AfterEachCallback, BeforeAllCallback,
    AfterAllCallback, ParameterResolver {

    public static class DockerMockSmtp {
        private static final Logger LOGGER = LoggerFactory.getLogger(DockerMockSmtp.class);

        private final DockerContainer mockSmtpServer;

        DockerMockSmtp() {
            mockSmtpServer = DockerContainer.fromName(Images.MOCK_SMTP_SERVER)
                .withLogConsumer(outputFrame -> LOGGER.debug("MockSMTP: " + outputFrame.getUtf8String()));
        }

        void start() {
            mockSmtpServer.start();
        }

        void stop() {
            mockSmtpServer.stop();
        }

        public ConfigurationClient getConfigurationClient() {
            return ConfigurationClient.from(Host.from(
                mockSmtpServer.getHostIp(),
                mockSmtpServer.getMappedPort(8000)));
        }

        public String getIPAddress() {
            return mockSmtpServer.getContainerIp();
        }
    }

    private final DockerMockSmtp dockerMockSmtp;

    public MockSmtpServerExtension() {
        this.dockerMockSmtp = new DockerMockSmtp();
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        dockerMockSmtp.start();
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        dockerMockSmtp.getConfigurationClient()
            .cleanServer();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        dockerMockSmtp.stop();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == DockerMockSmtp.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return dockerMockSmtp;
    }
}
