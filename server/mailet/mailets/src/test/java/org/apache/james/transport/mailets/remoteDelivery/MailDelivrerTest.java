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

package org.apache.james.transport.mailets.remoteDelivery;

import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import javax.mail.Address;
import javax.mail.SendFailedException;
import javax.mail.internet.InternetAddress;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.mailet.Mail;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.mail.smtp.SMTPSenderFailedException;

public class MailDelivrerTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailDelivrerTest.class);

    private MailDelivrer testee;
    private Bouncer bouncer;

    @Before
    public void setUp() {
        bouncer = mock(Bouncer.class);
        testee = new MailDelivrer(mock(RemoteDeliveryConfiguration.class), mock(MailDelivrerToHost.class), mock(DNSService.class), bouncer, LOGGER);
    }

    @Test
    public void handleSenderFailedExceptionShouldReturnTemporaryFailureByDefault() throws Exception {
        Mail mail = FakeMail.builder().recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

        SendFailedException sfe = new SendFailedException();
        ExecutionResult executionResult = testee.handleSenderFailedException(mail, sfe);

        assertThat(executionResult).isEqualTo(ExecutionResult.temporaryFailure(sfe));
    }

    @Test
    public void handleSenderFailedExceptionShouldReturnTemporaryFailureWhenNotServerException() throws Exception {
        Mail mail = FakeMail.builder().recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

        SendFailedException sfe = new SMTPSenderFailedException(new InternetAddress(MailAddressFixture.OTHER_AT_JAMES.asString()), "Comand", 400, "An temporary error");
        ExecutionResult executionResult = testee.handleSenderFailedException(mail, sfe);

        assertThat(executionResult).isEqualTo(ExecutionResult.temporaryFailure(sfe));
    }

    @Test
    public void handleSenderFailedExceptionShouldReturnPermanentFailureWhenServerException() throws Exception {
        Mail mail = FakeMail.builder().recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

        SendFailedException sfe = new SMTPSenderFailedException(new InternetAddress(MailAddressFixture.OTHER_AT_JAMES.asString()), "Comand", 505, "An temporary error");
        ExecutionResult executionResult = testee.handleSenderFailedException(mail, sfe);

        assertThat(executionResult).isEqualTo(ExecutionResult.permanentFailure(sfe));
    }

    @Test
    public void handleSenderFailedExceptionShouldReturnPermanentFailureWhenInvalidAndNotValidUnsent() throws Exception {
        Mail mail = FakeMail.builder().recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

        Address[] validSent = {};
        Address[] validUnsent = {};
        Address[] invalid = {new InternetAddress(MailAddressFixture.ANY_AT_JAMES.asString())};
        SendFailedException sfe = new SendFailedException("Message",
            new Exception(),
            validSent,
            validUnsent,
            invalid);
        ExecutionResult executionResult = testee.handleSenderFailedException(mail, sfe);

        assertThat(executionResult).isEqualTo(ExecutionResult.permanentFailure(sfe));
    }

    @Test
    public void handleSenderFailedExceptionShouldReturnTemporaryFailureWhenValidUnsent() throws Exception {
        Mail mail = FakeMail.builder().recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

        Address[] validSent = {};
        Address[] validUnsent = {new InternetAddress(MailAddressFixture.OTHER_AT_JAMES.asString())};
        Address[] invalid = {};
        SendFailedException sfe = new SendFailedException("Message",
            new Exception(),
            validSent,
            validUnsent,
            invalid);
        ExecutionResult executionResult = testee.handleSenderFailedException(mail, sfe);

        assertThat(executionResult).isEqualTo(ExecutionResult.temporaryFailure(sfe));
    }

    @Test
    public void handleSenderFailedExceptionShouldReturnTemporaryFailureWhenInvalidAndValidUnsent() throws Exception {
        Mail mail = FakeMail.builder().recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

        Address[] validSent = {};
        Address[] validUnsent = {new InternetAddress(MailAddressFixture.OTHER_AT_JAMES.asString())};
        Address[] invalid = {new InternetAddress(MailAddressFixture.ANY_AT_JAMES.asString())};
        SendFailedException sfe = new SendFailedException("Message",
            new Exception(),
            validSent,
            validUnsent,
            invalid);
        ExecutionResult executionResult = testee.handleSenderFailedException(mail, sfe);

        assertThat(executionResult).isEqualTo(ExecutionResult.temporaryFailure(sfe));
    }

    @Test
    public void handleSenderFailedExceptionShouldSetRecipientToInvalidWhenOnlyInvalid() throws Exception {
        Mail mail = FakeMail.builder().recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

        Address[] validSent = {};
        Address[] validUnsent = {};
        Address[] invalid = {new InternetAddress(MailAddressFixture.ANY_AT_JAMES.asString())};
        SendFailedException sfe = new SendFailedException("Message",
            new Exception(),
            validSent,
            validUnsent,
            invalid);
        testee.handleSenderFailedException(mail, sfe);

        assertThat(mail.getRecipients()).containsOnly(MailAddressFixture.ANY_AT_JAMES);
    }

    @Test
    public void handleSenderFailedExceptionShouldSetRecipientToValidUnsentWhenOnlyValidUnsent() throws Exception {
        Mail mail = FakeMail.builder().recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

        Address[] validSent = {};
        Address[] validUnsent = {new InternetAddress(MailAddressFixture.OTHER_AT_JAMES.asString())};
        Address[] invalid = {};
        SendFailedException sfe = new SendFailedException("Message",
            new Exception(),
            validSent,
            validUnsent,
            invalid);
        testee.handleSenderFailedException(mail, sfe);

        assertThat(mail.getRecipients()).containsOnly(MailAddressFixture.OTHER_AT_JAMES);
    }

    @Test
    public void handleSenderFailedExceptionShouldSetRecipientToValidUnsentWhenValidUnsentAndInvalid() throws Exception {
        Mail mail = FakeMail.builder().recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

        Address[] validSent = {};
        Address[] validUnsent = {new InternetAddress(MailAddressFixture.OTHER_AT_JAMES.asString())};
        Address[] invalid = {new InternetAddress(MailAddressFixture.ANY_AT_JAMES.asString())};
        SendFailedException sfe = new SendFailedException("Message",
            new Exception(),
            validSent,
            validUnsent,
            invalid);
        testee.handleSenderFailedException(mail, sfe);

        assertThat(mail.getRecipients()).containsOnly(MailAddressFixture.OTHER_AT_JAMES);
    }

    @Test
    public void handleSenderFailedExceptionShouldBounceInvalidAddressesOnBothInvalidAndValidUnsent() throws Exception {
        Mail mail = FakeMail.builder().recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

        Address[] validSent = {};
        Address[] validUnsent = {new InternetAddress(MailAddressFixture.OTHER_AT_JAMES.asString())};
        Address[] invalid = {new InternetAddress(MailAddressFixture.ANY_AT_JAMES.asString())};
        SendFailedException sfe = new SendFailedException("Message",
            new Exception(),
            validSent,
            validUnsent,
            invalid);
        testee.handleSenderFailedException(mail, sfe);

        verify(bouncer).bounce(mail, sfe);
        verifyNoMoreInteractions(bouncer);
    }
}
