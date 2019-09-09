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

import java.util.List;

import org.apache.james.mock.smtp.server.jackson.MailAddressModule;
import org.apache.james.mock.smtp.server.model.Mail;
import org.apache.james.mock.smtp.server.model.MockSMTPBehavior;
import org.apache.james.mock.smtp.server.model.MockSmtpBehaviors;
import org.apache.james.util.Host;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.annotations.VisibleForTesting;

import feign.Feign;
import feign.Logger;
import feign.RequestLine;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;

public interface ConfigurationClient {

    @VisibleForTesting
    static ConfigurationClient fromServer(HTTPConfigurationServer server) {
        return from(Host.from("localhost", server.getPort().getValue()));
    }

    static ConfigurationClient from(Host mockServerHttpHost) {
        return Feign.builder()
            .logger(new Slf4jLogger(ConfigurationClient.class))
            .logLevel(Logger.Level.FULL)
            .encoder(new JacksonEncoder(OBJECT_MAPPER))
            .decoder(new JacksonDecoder(OBJECT_MAPPER))
            .target(ConfigurationClient.class, "http://" + mockServerHttpHost.asString());
    }

    ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .registerModule(new Jdk8Module())
        .registerModule(new GuavaModule())
        .registerModule(MailAddressModule.MODULE);

    @RequestLine("PUT " + HTTPConfigurationServer.SMTP_BEHAVIORS)
    void setBehaviors(MockSmtpBehaviors behaviors);

    @RequestLine("DELETE " + HTTPConfigurationServer.SMTP_BEHAVIORS)
    void clearBehaviors();

    @RequestLine("GET " + HTTPConfigurationServer.SMTP_BEHAVIORS)
    List<MockSMTPBehavior> listBehaviors();

    @RequestLine("GET " + HTTPConfigurationServer.SMTP_MAILS)
    List<Mail> listMails();

    @RequestLine("DELETE " + HTTPConfigurationServer.SMTP_MAILS)
    void clearMails();

    default void setBehaviors(List<MockSMTPBehavior> behaviors) {
        setBehaviors(new MockSmtpBehaviors(behaviors));
    }

    default void cleanServer() {
        clearBehaviors();
        clearMails();
    }
}
