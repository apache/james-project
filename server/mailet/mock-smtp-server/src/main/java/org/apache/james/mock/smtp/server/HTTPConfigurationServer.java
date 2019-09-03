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
import org.apache.james.mock.smtp.server.jackson.MailAddressModule;
import org.apache.james.mock.smtp.server.model.Mails;
import org.apache.james.mock.smtp.server.model.MockSMTPBehaviorInformation;
import org.apache.james.mock.smtp.server.model.MockSmtpBehaviors;
import org.apache.james.util.Port;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.steveash.guavate.Guavate;

public class HTTPConfigurationServer {
    static class SMTPBehaviorsServlet extends HttpServlet {
        private final SMTPBehaviorRepository smtpBehaviorRepository;

        SMTPBehaviorsServlet(SMTPBehaviorRepository smtpBehaviorRepository) {
            this.smtpBehaviorRepository = smtpBehaviorRepository;
        }

        @Override
        protected void doPut(HttpServletRequest req, HttpServletResponse resp) {
            try {
                MockSmtpBehaviors behaviors = OBJECT_MAPPER.readValue(req.getInputStream(), MockSmtpBehaviors.class);
                smtpBehaviorRepository.setBehaviors(behaviors);
                resp.setStatus(SC_NO_CONTENT);
            } catch (IOException e) {
                resp.setStatus(SC_BAD_REQUEST);
            }
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            MockSmtpBehaviors mockSmtpBehaviors = new MockSmtpBehaviors(smtpBehaviorRepository.remainingBehaviors()
                .map(MockSMTPBehaviorInformation::getBehavior)
                .collect(Guavate.toImmutableList()));

            resp.setStatus(SC_OK);
            resp.setContentType("application/json");
            OBJECT_MAPPER.writeValue(resp.getOutputStream(), mockSmtpBehaviors);
        }

        @Override
        protected void doDelete(HttpServletRequest req, HttpServletResponse resp) {
            smtpBehaviorRepository.clearBehaviors();
            resp.setStatus(SC_NO_CONTENT);
        }
    }

    static class SMTPMailsServlet extends HttpServlet {
        private final ReceivedMailRepository receivedMailRepository;

        SMTPMailsServlet(ReceivedMailRepository receivedMailRepository) {
            this.receivedMailRepository = receivedMailRepository;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            Mails mails = new Mails(receivedMailRepository.list());
            resp.setStatus(SC_OK);
            resp.setContentType("application/json");
            OBJECT_MAPPER.writeValue(resp.getOutputStream(), mails);
        }

        @Override
        protected void doDelete(HttpServletRequest req, HttpServletResponse resp) {
            receivedMailRepository.clear();
            resp.setStatus(SC_NO_CONTENT);
        }
    }

    private static final String SMTP_BEHAVIORS = "/smtpBehaviors";
    private static final String SMTP_MAILS = "/smtpMails";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .registerModule(new Jdk8Module())
        .registerModule(new GuavaModule())
        .registerModule(MailAddressModule.MODULE);

    public static HTTPConfigurationServer onRandomPort(SMTPBehaviorRepository smtpBehaviorRepository, ReceivedMailRepository receivedMailRepository) {
        return new HTTPConfigurationServer(smtpBehaviorRepository,
            receivedMailRepository,
            Configuration.builder().randomPort());
    }

    public static HTTPConfigurationServer onPort(SMTPBehaviorRepository smtpBehaviorRepository, ReceivedMailRepository receivedMailRepository, Port port) {
        return new HTTPConfigurationServer(smtpBehaviorRepository,
            receivedMailRepository,
            Configuration.builder().port(port.getValue()));
    }

    private final JettyHttpServer jettyHttpServer;

    private HTTPConfigurationServer(SMTPBehaviorRepository smtpBehaviorRepository, ReceivedMailRepository receivedMailRepository, Configuration.Builder configurationBuilder) {
        jettyHttpServer = JettyHttpServer.create(configurationBuilder.serve(SMTP_BEHAVIORS)
            .with(new SMTPBehaviorsServlet(smtpBehaviorRepository))
            .serve(SMTP_MAILS)
            .with(new SMTPMailsServlet(receivedMailRepository))
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
