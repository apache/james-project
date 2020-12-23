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
import org.apache.james.mock.smtp.server.model.Condition;
import org.apache.james.mock.smtp.server.model.Mail;
import org.apache.james.mock.smtp.server.model.MockSMTPBehavior;
import org.apache.james.mock.smtp.server.model.MockSmtpBehaviors;
import org.apache.james.mock.smtp.server.model.Operator;
import org.apache.james.mock.smtp.server.model.Response;
import org.apache.james.mock.smtp.server.model.SMTPCommand;
import org.apache.james.mock.smtp.server.model.SMTPExtensions;
import org.apache.james.util.Host;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import feign.Feign;
import feign.Logger;
import feign.RequestLine;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;

public interface ConfigurationClient {

    class BehaviorsParamsBuilder {

        public static class CommandStep {
            private final BehaviorsParamsBuilder backReference;

            CommandStep(BehaviorsParamsBuilder backReference) {
                this.backReference = backReference;
            }

            public ConditionStep expect(SMTPCommand command) {
                Preconditions.checkNotNull(command, "'command' should not be null");
                return new ConditionStep(backReference, command);
            }
        }

        public static class ConditionStep {
            public static Condition anyInput() {
                return Condition.MATCH_ALL;
            }

            public static Condition inputContaining(String value) {
                return new Condition.OperatorCondition(Operator.CONTAINS, value);
            }

            private final BehaviorsParamsBuilder backReference;
            private final SMTPCommand command;

            ConditionStep(BehaviorsParamsBuilder backReference, SMTPCommand command) {
                this.backReference = backReference;
                this.command = command;
            }

            public BehaviorsParamsBuilder.ResponseStep matching(Condition condition) {
                return new BehaviorsParamsBuilder.ResponseStep(backReference, command, condition);
            }
        }

        public static class ResponseStep {
            public static Response serviceNotAvailable(String message) {
                return new Response(Response.SMTPStatusCode.SERVICE_NOT_AVAILABLE_421, message);
            }

            public static Response doesNotAcceptAnyMail(String message) {
                return new Response(Response.SMTPStatusCode.DOES_NOT_ACCEPT_MAIL_521, message);
            }

            private final BehaviorsParamsBuilder backReference;
            private final SMTPCommand command;
            private final Condition condition;

            ResponseStep(BehaviorsParamsBuilder backReference, SMTPCommand command, Condition condition) {
                this.backReference = backReference;
                this.command = command;
                this.condition = condition;
            }

            public BehaviorsParamsBuilder.NumberOfAnswerStep thenRespond(Response response) {
                return new BehaviorsParamsBuilder.NumberOfAnswerStep(backReference, command, response, condition);
            }
        }

        public static class NumberOfAnswerStep {
            private final BehaviorsParamsBuilder backReference;
            private final SMTPCommand command;
            private final Response response;
            private final Condition condition;

            NumberOfAnswerStep(BehaviorsParamsBuilder backReference, SMTPCommand command, Response response, Condition condition) {
                this.backReference = backReference;
                this.command = command;
                this.response = response;
                this.condition = condition;
            }

            public BehaviorsParamsBuilder anyTimes() {
                return backReference.add(toBehavior(MockSMTPBehavior.NumberOfAnswersPolicy.anytime()));
            }

            public BehaviorsParamsBuilder onlySomeTimes(int count) {
                return backReference.add(toBehavior(MockSMTPBehavior.NumberOfAnswersPolicy.times(count)));
            }

            MockSMTPBehavior toBehavior(MockSMTPBehavior.NumberOfAnswersPolicy numberOfAnswersPolicy) {
                return new MockSMTPBehavior(command, condition, response, numberOfAnswersPolicy);
            }
        }

        private final ImmutableList.Builder<MockSMTPBehavior> behaviors;
        private final ConfigurationClient client;

        public BehaviorsParamsBuilder(ConfigurationClient client) {
            this.behaviors = ImmutableList.builder();
            this.client = client;
        }

        public BehaviorsParamsBuilder.CommandStep addNewBehavior() {
            return new ConfigurationClient.BehaviorsParamsBuilder.CommandStep(this);
        }

        BehaviorsParamsBuilder add(MockSMTPBehavior behavior) {
            this.behaviors.add(behavior);
            return this;
        }

        public void post() {
            client.setBehaviors(behaviors.build());
        }
    }

    @VisibleForTesting
    static ConfigurationClient fromServer(HTTPConfigurationServer.RunningStage server) {
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

    @RequestLine("PUT " + HTTPConfigurationServer.SMTP_EXTENSIONS)
    void setSMTPExtensions(SMTPExtensions extensions);

    @RequestLine("DELETE " + HTTPConfigurationServer.SMTP_EXTENSIONS)
    void clearSMTPExtensions();

    @RequestLine("GET " + HTTPConfigurationServer.SMTP_EXTENSIONS)
    SMTPExtensions listSMTPExtensions();

    @RequestLine("GET " + HTTPConfigurationServer.SMTP_MAILS)
    List<Mail> listMails();

    @RequestLine("GET " + HTTPConfigurationServer.VERSION)
    String version();

    @RequestLine("DELETE " + HTTPConfigurationServer.SMTP_MAILS)
    void clearMails();

    default void setBehaviors(List<MockSMTPBehavior> behaviors) {
        setBehaviors(new MockSmtpBehaviors(behaviors));
    }

    default void cleanServer() {
        clearBehaviors();
        clearMails();
    }

    default BehaviorsParamsBuilder.CommandStep addNewBehavior() {
        return new BehaviorsParamsBuilder(this)
            .addNewBehavior();
    }
}
