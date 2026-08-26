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

package org.apache.james.protocols.smtp;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;

import jakarta.mail.internet.AddressException;

import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.io.FileHandler;
import org.apache.james.core.MailAddress;
import org.junit.jupiter.api.Test;

class SMTPErrorMessagesTest {
    private static final MailAddress RECIPIENT = recipient();

    private static MailAddress recipient() {
        try {
            return new MailAddress("bob@domain.tld");
        } catch (AddressException e) {
            throw new RuntimeException(e);
        }
    }

    private static SMTPErrorMessages parse(String xml) throws ConfigurationException {
        XMLConfiguration configuration = new XMLConfiguration();
        new FileHandler(configuration).load(new ByteArrayInputStream(xml.getBytes(UTF_8)));
        return SMTPErrorMessages.parse(configuration);
    }

    @Test
    void shouldFallBackToDefaultsWhenNotConfigured() throws Exception {
        SMTPErrorMessages errorMessages = parse("<smtpserver/>");

        assertThat(errorMessages.oversizedMailMessage())
            .isEqualTo("Message size exceeds fixed maximum message size");
        assertThat(errorMessages.unknownUserMessage(RECIPIENT))
            .isEqualTo("Unknown user: bob@domain.tld");
    }

    @Test
    void shouldFallBackToDefaultsWhenEmptySection() throws Exception {
        SMTPErrorMessages errorMessages = parse("<smtpserver><errorMessages/></smtpserver>");

        assertThat(errorMessages.oversizedMailMessage())
            .isEqualTo("Message size exceeds fixed maximum message size");
        assertThat(errorMessages.unknownUserMessage(RECIPIENT))
            .isEqualTo("Unknown user: bob@domain.tld");
    }

    @Test
    void shouldReturnConfiguredMessages() throws Exception {
        SMTPErrorMessages errorMessages = parse("<smtpserver>" +
            "  <errorMessages>" +
            "    <oversizedMail>Votre message est trop volumineux</oversizedMail>" +
            "    <unknownUser>Ce destinataire n'existe pas</unknownUser>" +
            "  </errorMessages>" +
            "</smtpserver>");

        assertThat(errorMessages.oversizedMailMessage())
            .isEqualTo("Votre message est trop volumineux");
        assertThat(errorMessages.unknownUserMessage(RECIPIENT))
            .isEqualTo("Ce destinataire n'existe pas bob@domain.tld");
    }

    @Test
    void configuredUnknownUserMessageShouldStillDiscloseTheRecipient() throws Exception {
        SMTPErrorMessages errorMessages = parse("<smtpserver>" +
            "  <errorMessages>" +
            "    <unknownUser>The recipient does not exist.</unknownUser>" +
            "  </errorMessages>" +
            "</smtpserver>");

        assertThat(errorMessages.unknownUserMessage(RECIPIENT))
            .isEqualTo("The recipient does not exist. bob@domain.tld");
    }
}
