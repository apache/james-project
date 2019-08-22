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

import javax.mail.internet.AddressException;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.MailAddress;
import org.apache.james.mock.smtp.server.model.Mail;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.RejectException;
import org.subethamail.smtp.TooMuchDataException;

public class MockMessageHandler implements MessageHandler {

    private static final int ARGUMENT_SYNTAX_ERROR_501 = 501;

    private final Mail.Envelope.Builder envelopeBuilder;
    private final Mail.Builder mailBuilder;
    private final ReceivedMailRepository mailRepository;

    MockMessageHandler(ReceivedMailRepository mailRepository) {
        this.mailRepository = mailRepository;
        this.envelopeBuilder = new Mail.Envelope.Builder();
        this.mailBuilder = new Mail.Builder();
    }

    @Override
    public void from(String from) throws RejectException {
        try {
            envelopeBuilder.from(new MailAddress(from));
        } catch (AddressException e) {
            throw new RejectException(ARGUMENT_SYNTAX_ERROR_501, "invalid email address supplied");
        }
    }

    @Override
    public void recipient(String recipient) throws RejectException {
        try {
            envelopeBuilder.addRecipient(new MailAddress(recipient));
        } catch (AddressException e) {
            throw new RejectException(ARGUMENT_SYNTAX_ERROR_501, "invalid email address supplied");
        }
    }

    @Override
    public void data(InputStream data) throws RejectException, TooMuchDataException, IOException {
        mailBuilder.message(IOUtils.toString(data, StandardCharsets.UTF_8));
    }

    @Override
    public void done() {
        Mail mail = mailBuilder.envelope(envelopeBuilder.build())
            .build();
        mailRepository.store(mail);
    }
}
