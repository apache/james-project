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

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.NO_CONTENT;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.apache.james.mock.smtp.server.jackson.MailAddressModule;
import org.apache.james.mock.smtp.server.model.Mails;
import org.apache.james.mock.smtp.server.model.MockSMTPBehaviorInformation;
import org.apache.james.mock.smtp.server.model.MockSmtpBehaviors;
import org.apache.james.mock.smtp.server.model.SMTPExtension;
import org.apache.james.mock.smtp.server.model.SMTPExtensions;
import org.apache.james.util.Port;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.steveash.guavate.Guavate;

import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

public class HTTPConfigurationServer {

    public static final String APPLICATION_JSON = "application/json";

    public static class Configuration {
        static Configuration port(Port port) {
            return new Configuration(Optional.of(port));
        }

        static Configuration randomPort() {
            return new Configuration(Optional.empty());
        }

        private final Optional<Port> port;

        Configuration(Optional<Port> port) {
            this.port = port;
        }

        Optional<Port> getPort() {
            return port;
        }
    }

    public static class RunningStage {
        private final DisposableServer server;

        private RunningStage(DisposableServer server) {
            this.server = server;
        }

        public Port getPort() {
            return Port.of(server.port());
        }

        public void stop() {
            server.disposeNow();
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(HTTPConfigurationServer.class);
    private static final int RANDOM_PORT = 0;
    static final String SMTP_BEHAVIORS = "/smtpBehaviors";
    static final String SMTP_EXTENSIONS = "/smtpExtensions";
    static final String VERSION = "/version";
    static final String SMTP_MAILS = "/smtpMails";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .registerModule(new Jdk8Module())
        .registerModule(new GuavaModule())
        .registerModule(MailAddressModule.MODULE);

    public static HTTPConfigurationServer onRandomPort(SMTPBehaviorRepository smtpBehaviorRepository, ReceivedMailRepository receivedMailRepository) {
        return new HTTPConfigurationServer(smtpBehaviorRepository,
            receivedMailRepository,
            Configuration.randomPort());
    }

    public static HTTPConfigurationServer onPort(SMTPBehaviorRepository smtpBehaviorRepository, ReceivedMailRepository receivedMailRepository, Port port) {
        return new HTTPConfigurationServer(smtpBehaviorRepository,
            receivedMailRepository,
            Configuration.port(port));
    }

    private final SMTPBehaviorRepository smtpBehaviorRepository;
    private final ReceivedMailRepository receivedMailRepository;
    private final Configuration configuration;

    private HTTPConfigurationServer(SMTPBehaviorRepository smtpBehaviorRepository, ReceivedMailRepository receivedMailRepository, Configuration configuration) {
        this.smtpBehaviorRepository = smtpBehaviorRepository;
        this.receivedMailRepository = receivedMailRepository;
        this.configuration = configuration;
    }

    public RunningStage start() {
        return new RunningStage(HttpServer.create()
            .port(configuration.getPort()
                .map(Port::getValue)
                .orElse(RANDOM_PORT))
            .route(routes -> routes
                .get(SMTP_BEHAVIORS, this::getBehaviors)
                .get(SMTP_EXTENSIONS, this::getExtensions)
                .get(VERSION, this::getVersion)
                .put(SMTP_BEHAVIORS, this::putBehaviors)
                .put(SMTP_EXTENSIONS, this::putExtensions)
                .delete(SMTP_BEHAVIORS, this::deleteBehaviors)
                .delete(SMTP_EXTENSIONS, this::deleteExtensions)
                .get(SMTP_MAILS, this::getMails)
                .delete(SMTP_MAILS, this::deleteMails))
            .bindNow());
    }

    private Publisher<Void> getBehaviors(HttpServerRequest req, HttpServerResponse res) {
        MockSmtpBehaviors mockSmtpBehaviors = new MockSmtpBehaviors(smtpBehaviorRepository.remainingBehaviors()
            .map(MockSMTPBehaviorInformation::getBehavior)
            .collect(Guavate.toImmutableList()));

        try {
            return res.status(OK)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .sendString(Mono.just(OBJECT_MAPPER.writeValueAsString(mockSmtpBehaviors)));
        } catch (JsonProcessingException e) {
            LOGGER.error("Could not serialize JSON", e);
            return res.status(INTERNAL_SERVER_ERROR).send();
        }
    }

    private Publisher<Void> getExtensions(HttpServerRequest req, HttpServerResponse res) {
        List<SMTPExtension> extensions = smtpBehaviorRepository.getSMTPExtensions();

        try {
            return res.status(OK)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .sendString(Mono.just(OBJECT_MAPPER.writeValueAsString(extensions)));
        } catch (JsonProcessingException e) {
            LOGGER.error("Could not serialize JSON", e);
            return res.status(INTERNAL_SERVER_ERROR).send();
        }
    }

    private Publisher<Void> getVersion(HttpServerRequest req, HttpServerResponse res) {
        return res.status(OK)
            .sendString(Mono.just("0.4"));
    }

    private Publisher<Void> putBehaviors(HttpServerRequest req, HttpServerResponse res) {
        return req.receive().aggregate().asInputStream()
            .flatMap(inputStream -> {
                try {
                    MockSmtpBehaviors behaviors = OBJECT_MAPPER.readValue(inputStream, MockSmtpBehaviors.class);
                    smtpBehaviorRepository.setBehaviors(behaviors);
                    return res.status(NO_CONTENT).send();
                } catch (IOException e) {
                    LOGGER.info("Bad request", e);
                    return res.status(BAD_REQUEST).send();
                }
            });
    }

    private Publisher<Void> putExtensions(HttpServerRequest req, HttpServerResponse res) {
        return req.receive().aggregate().asInputStream()
            .flatMap(inputStream -> {
                try {
                    List<SMTPExtension> extensions = OBJECT_MAPPER.readValue(inputStream, SMTPExtensions.class).getSmtpExtensions();
                    smtpBehaviorRepository.setSmtpExtensions(extensions);
                    return res.status(NO_CONTENT).send();
                } catch (IOException e) {
                    LOGGER.info("Bad request", e);
                    return res.status(BAD_REQUEST).send();
                }
            });
    }

    private Publisher<Void> deleteBehaviors(HttpServerRequest req, HttpServerResponse res) {
        smtpBehaviorRepository.clearBehaviors();
        return res.status(NO_CONTENT).send();
    }

    private Publisher<Void> deleteExtensions(HttpServerRequest req, HttpServerResponse res) {
        smtpBehaviorRepository.clearExtensions();
        return res.status(NO_CONTENT).send();
    }

    private Publisher<Void> deleteMails(HttpServerRequest req, HttpServerResponse res) {
        receivedMailRepository.clear();
        return res.status(NO_CONTENT).send();
    }

    private Publisher<Void> getMails(HttpServerRequest req, HttpServerResponse res) {
        Mails mails = new Mails(receivedMailRepository.list());

        try {
            return res.status(OK)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .sendString(Mono.just(OBJECT_MAPPER.writeValueAsString(mails)));
        } catch (JsonProcessingException e) {
            LOGGER.error("Could not serialize JSON", e);
            return res.status(INTERNAL_SERVER_ERROR).send();
        }
    }
}
