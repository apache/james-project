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
import java.util.Optional;

import javax.mail.internet.AddressException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.core.MailAddress;
import org.apache.james.mock.smtp.server.model.Mail;
import org.apache.james.mock.smtp.server.model.MockSMTPBehavior;
import org.apache.james.mock.smtp.server.model.MockSMTPBehaviorInformation;
import org.apache.james.mock.smtp.server.model.Response;
import org.apache.james.mock.smtp.server.model.Response.SMTPStatusCode;
import org.apache.james.mock.smtp.server.model.SMTPCommand;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.RejectException;
import org.subethamail.smtp.TooMuchDataException;

public class MockMessageHandler implements MessageHandler {

    @FunctionalInterface
    interface Behavior<T> {
        void behave(T input) throws RejectException;
    }

    class MockBehavior<T> implements Behavior<T> {

        private final MockSMTPBehavior behavior;

        MockBehavior(MockSMTPBehavior behavior) {
            this.behavior = behavior;
        }

        @Override
        public void behave(T input) throws RejectException {
            Response response = behavior.getResponse();
            if (response.isServerRejected()) {
                throw new RejectException(response.getCode().getRawCode(), response.getMessage());
            }
            throw new NotImplementedException("Not rejecting commands in mock behaviours is not supported yet");
        }
    }

    class SMTPBehaviorRepositoryUpdater<T> implements Behavior<T> {

        private final SMTPBehaviorRepository behaviorRepository;
        private final MockBehavior<T> actualBehavior;

        SMTPBehaviorRepositoryUpdater(SMTPBehaviorRepository behaviorRepository, MockSMTPBehavior behavior) {
            this.behaviorRepository = behaviorRepository;
            this.actualBehavior = new MockBehavior<>(behavior);
        }

        @Override
        public void behave(T input) throws RejectException {
            try {
                actualBehavior.behave(input);
            } finally {
                behaviorRepository.decreaseRemainingAnswers(actualBehavior.behavior);
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
        Optional<Behavior<MailAddress>> fromBehavior = firstMatchedBehavior(SMTPCommand.MAIL_FROM);

        fromBehavior
            .orElseGet(() -> envelopeBuilder::from)
            .behave(parse(from));
    }

    @Override
    public void recipient(String recipient) throws RejectException {
        Optional<Behavior<MailAddress>> recipientBehavior = firstMatchedBehavior(SMTPCommand.RCPT_TO);

        recipientBehavior
            .orElseGet(() -> envelopeBuilder::addRecipient)
            .behave(parse(recipient));
    }

    @Override
    public void data(InputStream data) throws RejectException, TooMuchDataException, IOException {
        Optional<Behavior<InputStream>> dataBehavior = firstMatchedBehavior(SMTPCommand.DATA);

        dataBehavior
            .orElseGet(() -> content -> mailBuilder.message(readData(content)))
            .behave(data);
    }

    private <T> Optional<Behavior<T>> firstMatchedBehavior(SMTPCommand data) {
        return behaviorRepository.remainingBehaviors()
            .map(MockSMTPBehaviorInformation::getBehavior)
            .filter(behavior -> behavior.getCommand().equals(data))
            .findFirst()
            .map(mockBehavior -> new SMTPBehaviorRepositoryUpdater<>(behaviorRepository, mockBehavior));
    }

    @Override
    public void done() {
        Mail mail = mailBuilder.envelope(envelopeBuilder.build())
            .build();
        mailRepository.store(mail);
    }

    private String readData(InputStream data) {
        try {
            return IOUtils.toString(data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RejectException(SMTPStatusCode.SYNTAX_ERROR_IN_PARAMETERS_OR_ARGUMENTS_501.getRawCode(), "invalid data supplied");
        }
    }

    private MailAddress parse(String mailAddress) {
        try {
            return new MailAddress(mailAddress);
        } catch (AddressException e) {
            throw new RejectException(SMTPStatusCode.SYNTAX_ERROR_IN_PARAMETERS_OR_ARGUMENTS_501.getRawCode(), "invalid email address supplied");
        }
    }
}
