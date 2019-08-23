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

package org.apache.james.mock.smtp.server;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.james.http.jetty.Configuration;
import org.apache.james.http.jetty.JettyHttpServer;
import org.apache.james.util.Port;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.ImmutableList;

public class HTTPConfigurationServer {
    static class HTTPConfigurationServlet extends HttpServlet {
        private final ObjectMapper objectMapper;
        private final SMTPBehaviorRepository smtpBehaviorRepository;

        HTTPConfigurationServlet(SMTPBehaviorRepository smtpBehaviorRepository) {
            this.objectMapper = new ObjectMapper()
                .registerModule(new Jdk8Module())
                .registerModule(new GuavaModule());
            this.smtpBehaviorRepository = smtpBehaviorRepository;
        }

        @Override
        protected void doPut(HttpServletRequest req, HttpServletResponse resp) {
            try {
                MockSmtpBehaviors behaviors = objectMapper.readValue(req.getInputStream(), MockSmtpBehaviors.class);
                smtpBehaviorRepository.setBehaviors(behaviors);
                resp.setStatus(SC_NO_CONTENT);
            } catch (IOException e) {
                resp.setStatus(SC_BAD_REQUEST);
            }
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            MockSmtpBehaviors mockSmtpBehaviors = smtpBehaviorRepository.getBehaviors().orElse(new MockSmtpBehaviors(ImmutableList.of()));
            resp.setStatus(SC_OK);
            resp.setContentType("application/json");
            objectMapper.writeValue(resp.getOutputStream(), mockSmtpBehaviors);
        }

        @Override
        protected void doDelete(HttpServletRequest req, HttpServletResponse resp) {
            smtpBehaviorRepository.clearBehaviors();
            resp.setStatus(SC_NO_CONTENT);
        }
    }

    private static final String SMTP_BEHAVIORS = "/smtpBehaviors";

    public static HTTPConfigurationServer onRandomPort(SMTPBehaviorRepository smtpBehaviorRepository) {
        return new HTTPConfigurationServer(smtpBehaviorRepository,
            Configuration.builder().randomPort());
    }

    public static HTTPConfigurationServer onPort(SMTPBehaviorRepository smtpBehaviorRepository, Port port) {
        return new HTTPConfigurationServer(smtpBehaviorRepository,
            Configuration.builder().port(port.getValue()));
    }

    private final JettyHttpServer jettyHttpServer;

    private HTTPConfigurationServer(SMTPBehaviorRepository smtpBehaviorRepository, Configuration.Builder configurationBuilder) {
        jettyHttpServer = JettyHttpServer.create(configurationBuilder.serve(SMTP_BEHAVIORS)
            .with(new HTTPConfigurationServlet(smtpBehaviorRepository))
            .build());
    }

    public void start() throws Exception {
        jettyHttpServer.start();
    }

    public Port getPort() {
        return new Port(jettyHttpServer.getPort());
    }

    public void stop() throws Exception {
        jettyHttpServer.stop();
    }
}
