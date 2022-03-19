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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;

import jakarta.mail.internet.AddressException;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.MailAddress;
import org.apache.james.mock.smtp.server.model.Mail;
import org.apache.james.mock.smtp.server.model.Mail.Parameter;
import org.apache.james.mock.smtp.server.model.MockSMTPBehavior;
import org.apache.james.mock.smtp.server.model.MockSMTPBehaviorInformation;
import org.apache.james.mock.smtp.server.model.Response;
import org.apache.james.mock.smtp.server.model.Response.SMTPStatusCode;
import org.apache.james.mock.smtp.server.model.SMTPCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.RejectException;

public class MockMessageHandler implements MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockMessageHandler.class);

    @FunctionalInterface
    private interface Behavior<T> {
        void behave(T input) throws RejectException;
    }

    private static class MockBehavior<T> implements Behavior<T> {
        private final MockSMTPBehavior behavior;

        MockBehavior(MockSMTPBehavior behavior) {
            this.behavior = behavior;
        }

        @Override
        public void behave(T input) throws RejectException {
            Response response = behavior.getResponse();
            LOGGER.info("Applying behavior {}", behavior);
            throw new RejectException(response.getCode().getRawCode(), response.getMessage());
        }
    }

    private static class ComposedBehavior<T> implements Behavior<T> {
        private static class Builder<U> {
            private final Behavior<U> behavior1;

            private Builder(Behavior<U> behavior1) {
                this.behavior1 = behavior1;
            }

            ComposedBehavior<U> andThen(Behavior<U> behavior2) {
                return new ComposedBehavior<>(behavior1, behavior2);
            }
        }

        private static <V> Builder<V> startWith(Behavior<V> behavior1) {
            return new Builder<>(behavior1);
        }

        private final Behavior<T> behavior1;
        private final Behavior<T> behavior2;

        private ComposedBehavior(Behavior<T> behavior1, Behavior<T> behavior2) {
            this.behavior1 = behavior1;
            this.behavior2 = behavior2;
        }

        @Override
        public void behave(T input) throws RejectException {
            try {
                behavior1.behave(input);
            } finally {
                behavior2.behave(input);
            }
        }
    }

    private final Mail.Envelope.Builder envelopeBuilder;
    private final Mail.Builder mailBuilder;
    private final ReceivedMailRepository mailRepository;
    private final SMTPBehaviorRepository behaviorRepository;

    MockMessageHandler(ReceivedMailRepository mailRepository, SMTPBehaviorRepository behaviorRepository) {
        this.mailRepository = mailRepository;
        this.behaviorRepository = behaviorRepository;
        this.envelopeBuilder = new Mail.Envelope.Builder();
        this.mailBuilder = new Mail.Builder();
    }

    @Override
    public void from(String from) throws RejectException {
        Optional<Behavior<MailAddress>> fromBehavior = firstMatchedBehavior(SMTPCommand.MAIL_FROM, from);

        fromBehavior
            .orElseGet(() -> envelopeBuilder::from)
            .behave(parse(from));
    }

    public void from(String from, Collection<Parameter> parameters) throws RejectException {
        Optional<Behavior<MailAddress>> fromBehavior = firstMatchedBehavior(SMTPCommand.MAIL_FROM, from);

        fromBehavior
            .orElseGet(() -> mailAddress -> envelopeBuilder.from(mailAddress).mailParameters(parameters))
            .behave(parse(from));
    }

    @Override
    public void recipient(String recipient) throws RejectException {
        Optional<Behavior<MailAddress>> recipientBehavior = firstMatchedBehavior(SMTPCommand.RCPT_TO, recipient);

        recipientBehavior
            .orElseGet(() -> envelopeBuilder::addRecipientMailAddress)
            .behave(parse(recipient));
    }

    public void recipient(String recipient, Collection<Parameter> parameters) throws RejectException {
        Optional<Behavior<MailAddress>> recipientBehavior = firstMatchedBehavior(SMTPCommand.RCPT_TO, recipient);

        recipientBehavior
            .orElseGet(() -> address -> envelopeBuilder.addRecipient(Mail.Recipient.builder()
                .parameters(parameters)
                .address(address)
                .build()))
            .behave(parse(recipient));
    }

    @Override
    public void data(InputStream data) throws RejectException {
        String dataString = readData(data);
        Optional<Behavior<String>> dataBehavior = firstMatchedBehavior(SMTPCommand.DATA, dataString);

        dataBehavior
            .orElseGet(() -> mailBuilder::message)
            .behave(dataString);
    }

    private <T> Optional<Behavior<T>> firstMatchedBehavior(SMTPCommand data, String dataLine) {
        return behaviorRepository.remainingBehaviors()
            .map(MockSMTPBehaviorInformation::getBehavior)
            .filter(behavior -> behavior.getCommand().equals(data))
            .filter(behavior -> behavior.getCondition().matches(dataLine))
            .findFirst()
            .map(behavior -> ComposedBehavior.<T>startWith(new MockBehavior<>(behavior))
                .andThen(any -> behaviorRepository.decreaseRemainingAnswers(behavior)));
    }

    @Override
    public void done() {
        Mail mail = mailBuilder.envelope(envelopeBuilder.build())
            .build();
        mailRepository.store(mail);
        LOGGER.info("Storing mail with envelope {}", mail.getEnvelope());
    }

    private String readData(InputStream data) {
        try {
            return IOUtils.toString(data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Error reading data", e);
            throw new RejectException(SMTPStatusCode.SYNTAX_ERROR_IN_PARAMETERS_OR_ARGUMENTS_501.getRawCode(), "invalid data supplied");
        }
    }

    private MailAddress parse(String mailAddress) {
        if (mailAddress.isEmpty()) {
            return MailAddress.nullSender();
        }
        try {
            return new MailAddress(mailAddress);
        } catch (AddressException e) {
            LOGGER.error("Error parsing mail address '{}'", mailAddress, e);
            throw new RejectException(SMTPStatusCode.SYNTAX_ERROR_IN_PARAMETERS_OR_ARGUMENTS_501.getRawCode(), "invalid email address supplied");
        }
    }
}
