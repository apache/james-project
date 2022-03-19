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

package org.apache.james.transport.mailets.remote.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collection;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.InternetAddress;

import org.apache.james.dnsservice.api.TemporaryResolutionException;
import org.apache.james.domainlist.api.DomainList;
import org.apache.mailet.HostAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableIterator;
import com.sun.mail.smtp.SMTPSenderFailedException;

@SuppressWarnings("deprecation")
class MailDelivrerTest {
    public static final String MX1_HOSTNAME = "mx1." + MailAddressFixture.JAMES2_APACHE_ORG;
    public static final String MX2_HOSTNAME = "mx2." + MailAddressFixture.JAMES2_APACHE_ORG;
    public static final String SMTP_URI2 = "protocol://userid:password@host:119/file1";
    public static final String SMTP_URI1 = "protocol://userid:password@host:119/file2";
    public static final HostAddress HOST_ADDRESS_1 = new HostAddress(MX1_HOSTNAME, SMTP_URI1);
    public static final HostAddress HOST_ADDRESS_2 = new HostAddress(MX2_HOSTNAME, SMTP_URI2);

    private MailDelivrer testee;
    private Bouncer bouncer;
    private DnsHelper dnsHelper;
    private MailDelivrerToHost mailDelivrerToHost;

    @BeforeEach
    public void setUp() {
        bouncer = mock(Bouncer.class);
        dnsHelper = mock(DnsHelper.class);
        mailDelivrerToHost = mock(MailDelivrerToHost.class);
        RemoteDeliveryConfiguration configuration = new RemoteDeliveryConfiguration(FakeMailetConfig.builder()
            .setProperty(RemoteDeliveryConfiguration.MAX_DNS_PROBLEM_RETRIES, "3")
            .setProperty(RemoteDeliveryConfiguration.DEBUG, "true")
            .build(),
            mock(DomainList.class));
        testee = new MailDelivrer(configuration, mailDelivrerToHost, dnsHelper, bouncer, mock(MailetContext.class));
    }

    @Test
    void handleSenderFailedExceptionShouldReturnTemporaryFailureByDefault() throws Exception {
        Mail mail = FakeMail.builder().name("name").recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

        SendFailedException sfe = new SendFailedException();
        ExecutionResult executionResult = testee.handleSenderFailedException(mail, sfe);

        assertThat(executionResult).isEqualTo(ExecutionResult.temporaryFailure(sfe));
    }

    @Test
    void handleSenderFailedExceptionShouldReturnTemporaryFailureWhenNotServerException() throws Exception {
        Mail mail = FakeMail.builder().name("name").recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

        SendFailedException sfe = new SMTPSenderFailedException(new InternetAddress(MailAddressFixture.OTHER_AT_JAMES.asString()), "Comand", 400, "An temporary error");
        ExecutionResult executionResult = testee.handleSenderFailedException(mail, sfe);

        assertThat(executionResult).isEqualTo(ExecutionResult.temporaryFailure(sfe));
    }

    @Test
    void handleSenderFailedExceptionShouldReturnPermanentFailureWhenServerException() throws Exception {
        Mail mail = FakeMail.builder().name("name").recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

        SendFailedException sfe = new SMTPSenderFailedException(new InternetAddress(MailAddressFixture.OTHER_AT_JAMES.asString()), "Comand", 505, "An temporary error");
        ExecutionResult executionResult = testee.handleSenderFailedException(mail, sfe);

        assertThat(executionResult).isEqualTo(ExecutionResult.permanentFailure(sfe));
    }

    @Test
    void handleSenderFailedExceptionShouldReturnPermanentFailureWhenInvalidAndNotValidUnsent() throws Exception {
        Mail mail = FakeMail.builder().name("name").recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

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
    void handleSenderFailedExceptionShouldReturnTemporaryFailureWhenValidUnsent() throws Exception {
        Mail mail = FakeMail.builder().name("name").recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

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
    void handleSenderFailedExceptionShouldReturnTemporaryFailureWhenInvalidAndValidUnsent() throws Exception {
        Mail mail = FakeMail.builder().name("name").recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

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
    void handleSenderFailedExceptionShouldSetRecipientToInvalidWhenOnlyInvalid() throws Exception {
        Mail mail = FakeMail.builder().name("name").recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

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
    void handleSenderFailedExceptionShouldSetRecipientToValidUnsentWhenOnlyValidUnsent() throws Exception {
        Mail mail = FakeMail.builder().name("name").recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

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
    void handleSenderFailedExceptionShouldSetRecipientToValidUnsentWhenValidUnsentAndInvalid() throws Exception {
        Mail mail = FakeMail.builder().name("name").recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

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
    void handleSenderFailedExceptionShouldBounceInvalidAddressesOnBothInvalidAndValidUnsent() throws Exception {
        Mail mail = FakeMail.builder().name("name").recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

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

    @Test
    void deliverShouldReturnTemporaryFailureOnTemporaryResolutionException() throws Exception {
        Mail mail = FakeMail.builder().name("name").recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

        when(dnsHelper.retrieveHostAddressIterator(MailAddressFixture.JAMES_APACHE_ORG, false)).thenThrow(new TemporaryResolutionException());

        ExecutionResult executionResult = testee.deliver(mail);

        assertThat(executionResult.getExecutionState()).isEqualTo(ExecutionResult.ExecutionState.TEMPORARY_FAILURE);
    }

    @Test
    void deliverShouldReturnTemporaryErrorWhenFirstDNSProblem() throws Exception {
        Mail mail = FakeMail.builder().name("name").recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

        UnmodifiableIterator<HostAddress> empty = ImmutableList.<HostAddress>of().iterator();
        when(dnsHelper.retrieveHostAddressIterator(MailAddressFixture.JAMES_APACHE_ORG, false)).thenReturn(empty);

        ExecutionResult executionResult = testee.deliver(mail);

        assertThat(executionResult.getExecutionState()).isEqualTo(ExecutionResult.ExecutionState.TEMPORARY_FAILURE);
    }

    @Test
    void deliverShouldReturnTemporaryErrorWhenToleratedDNSProblem() throws Exception {
        Mail mail = FakeMail.builder().name("name").recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();
        DeliveryRetriesHelper.incrementRetries(mail);

        UnmodifiableIterator<HostAddress> empty = ImmutableList.<HostAddress>of().iterator();
        when(dnsHelper.retrieveHostAddressIterator(MailAddressFixture.JAMES_APACHE_ORG, false)).thenReturn(empty);

        ExecutionResult executionResult = testee.deliver(mail);

        assertThat(executionResult.getExecutionState()).isEqualTo(ExecutionResult.ExecutionState.TEMPORARY_FAILURE);
    }

    @Test
    void deliverShouldReturnPermanentErrorWhenLimitDNSProblemReached() throws Exception {
        Mail mail = FakeMail.builder().name("name").recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();
        DeliveryRetriesHelper.incrementRetries(mail);
        DeliveryRetriesHelper.incrementRetries(mail);
        DeliveryRetriesHelper.incrementRetries(mail);

        UnmodifiableIterator<HostAddress> empty = ImmutableList.<HostAddress>of().iterator();
        when(dnsHelper.retrieveHostAddressIterator(MailAddressFixture.JAMES_APACHE_ORG, false)).thenReturn(empty);

        ExecutionResult executionResult = testee.deliver(mail);

        assertThat(executionResult.getExecutionState()).isEqualTo(ExecutionResult.ExecutionState.PERMANENT_FAILURE);
    }

    @Test
    void deliverShouldReturnPermanentErrorWhenLimitDNSProblemExceeded() throws Exception {
        Mail mail = FakeMail.builder().name("name").recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

        DeliveryRetriesHelper.incrementRetries(mail);
        DeliveryRetriesHelper.incrementRetries(mail);
        DeliveryRetriesHelper.incrementRetries(mail);
        DeliveryRetriesHelper.incrementRetries(mail);

        UnmodifiableIterator<HostAddress> empty = ImmutableList.<HostAddress>of().iterator();
        when(dnsHelper.retrieveHostAddressIterator(MailAddressFixture.JAMES_APACHE_ORG, false)).thenReturn(empty);

        ExecutionResult executionResult = testee.deliver(mail);

        assertThat(executionResult.getExecutionState()).isEqualTo(ExecutionResult.ExecutionState.PERMANENT_FAILURE);
    }

    @Test
    void deliverShouldWork() throws Exception {
        Mail mail = FakeMail.builder().name("name").recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

        UnmodifiableIterator<HostAddress> dnsEntries = ImmutableList.of(
            HOST_ADDRESS_1,
            HOST_ADDRESS_2).iterator();
        when(dnsHelper.retrieveHostAddressIterator(MailAddressFixture.JAMES_APACHE_ORG, false)).thenReturn(dnsEntries);
        when(mailDelivrerToHost.tryDeliveryToHost(any(Mail.class), any(Collection.class), any(HostAddress.class)))
            .thenReturn(ExecutionResult.success());
        ExecutionResult executionResult = testee.deliver(mail);

        verify(mailDelivrerToHost, times(1)).tryDeliveryToHost(any(Mail.class), any(Collection.class), any(HostAddress.class));
        assertThat(executionResult.getExecutionState()).isEqualTo(ExecutionResult.ExecutionState.SUCCESS);
    }

    @Test
    void deliverShouldAbortWhenServerError() throws Exception {
        Mail mail = FakeMail.builder().name("name").recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

        UnmodifiableIterator<HostAddress> dnsEntries = ImmutableList.of(
            HOST_ADDRESS_1,
            HOST_ADDRESS_2).iterator();
        when(dnsHelper.retrieveHostAddressIterator(MailAddressFixture.JAMES_APACHE_ORG, false)).thenReturn(dnsEntries);
        when(mailDelivrerToHost.tryDeliveryToHost(any(Mail.class), any(Collection.class), any(HostAddress.class)))
            .thenThrow(new MessagingException("500 : Horrible way to manage Server Return code"));
        ExecutionResult executionResult = testee.deliver(mail);

        verify(mailDelivrerToHost, times(1)).tryDeliveryToHost(any(Mail.class), any(Collection.class), any(HostAddress.class));
        assertThat(executionResult.getExecutionState()).isEqualTo(ExecutionResult.ExecutionState.PERMANENT_FAILURE);
    }

    @Test
    void deliverShouldAbortWithTemporaryWhenMessagingExceptionCauseUnknown() throws Exception {
        Mail mail = FakeMail.builder().name("name").recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

        UnmodifiableIterator<HostAddress> dnsEntries = ImmutableList.of(
            HOST_ADDRESS_1,
            HOST_ADDRESS_2).iterator();
        when(dnsHelper.retrieveHostAddressIterator(MailAddressFixture.JAMES_APACHE_ORG, false)).thenReturn(dnsEntries);
        when(mailDelivrerToHost.tryDeliveryToHost(any(Mail.class), any(Collection.class), any(HostAddress.class)))
            .thenThrow(new MessagingException("400 : Horrible way to manage Server Return code"));
        ExecutionResult executionResult = testee.deliver(mail);

        verify(mailDelivrerToHost, times(1)).tryDeliveryToHost(any(Mail.class), any(Collection.class), any(HostAddress.class));
        assertThat(executionResult.getExecutionState()).isEqualTo(ExecutionResult.ExecutionState.TEMPORARY_FAILURE);
    }

    @Test
    void deliverShouldTryTwiceOnIOException() throws Exception {
        Mail mail = FakeMail.builder().name("name").recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

        UnmodifiableIterator<HostAddress> dnsEntries = ImmutableList.of(
            HOST_ADDRESS_1,
            HOST_ADDRESS_2).iterator();
        when(dnsHelper.retrieveHostAddressIterator(MailAddressFixture.JAMES_APACHE_ORG, false)).thenReturn(dnsEntries);
        when(mailDelivrerToHost.tryDeliveryToHost(any(Mail.class), any(Collection.class), eq(HOST_ADDRESS_1)))
            .thenThrow(new MessagingException("400 : Horrible way to manage Server Return code", new IOException()));
        when(mailDelivrerToHost.tryDeliveryToHost(any(Mail.class), any(Collection.class), eq(HOST_ADDRESS_2)))
            .thenReturn(ExecutionResult.success());
        ExecutionResult executionResult = testee.deliver(mail);

        verify(mailDelivrerToHost, times(2)).tryDeliveryToHost(any(Mail.class), any(Collection.class), any(HostAddress.class));
        assertThat(executionResult.getExecutionState()).isEqualTo(ExecutionResult.ExecutionState.SUCCESS);
    }

    @Test
    void deliverShouldAbortWhenServerErrorSFE() throws Exception {
        Mail mail = FakeMail.builder().name("name").recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

        UnmodifiableIterator<HostAddress> dnsEntries = ImmutableList.of(
            HOST_ADDRESS_1,
            HOST_ADDRESS_2).iterator();
        when(dnsHelper.retrieveHostAddressIterator(MailAddressFixture.JAMES_APACHE_ORG, false)).thenReturn(dnsEntries);
        when(mailDelivrerToHost.tryDeliveryToHost(any(Mail.class), any(Collection.class), any(HostAddress.class)))
            .thenThrow(new SMTPSenderFailedException(new InternetAddress(MailAddressFixture.ANY_AT_JAMES.toString()), "command", 505, "Big failure"));
        ExecutionResult executionResult = testee.deliver(mail);

        verify(mailDelivrerToHost, times(1)).tryDeliveryToHost(any(Mail.class), any(Collection.class), any(HostAddress.class));
        assertThat(executionResult.getExecutionState()).isEqualTo(ExecutionResult.ExecutionState.PERMANENT_FAILURE);
    }

    @Test
    void deliverShouldAttemptDeliveryOnlyOnceIfNoMoreValidUnsent() throws Exception {
        Mail mail = FakeMail.builder().name("name").recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();

        UnmodifiableIterator<HostAddress> dnsEntries = ImmutableList.of(
            HOST_ADDRESS_1,
            HOST_ADDRESS_2).iterator();
        when(dnsHelper.retrieveHostAddressIterator(MailAddressFixture.JAMES_APACHE_ORG, false)).thenReturn(dnsEntries);
        when(mailDelivrerToHost.tryDeliveryToHost(any(Mail.class), any(Collection.class), any(HostAddress.class)))
            .thenThrow(new SendFailedException());
        ExecutionResult executionResult = testee.deliver(mail);

        verify(mailDelivrerToHost, times(1)).tryDeliveryToHost(any(Mail.class), any(Collection.class), any(HostAddress.class));
        assertThat(executionResult.getExecutionState()).isEqualTo(ExecutionResult.ExecutionState.TEMPORARY_FAILURE);
    }

    @Test
    void deliverShouldAttemptDeliveryOnBothMXIfStillRecipients() throws Exception {
        Mail mail = FakeMail.builder().name("name").recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();
        Address[] validSent = {};
        Address[] validUnsent = {new InternetAddress(MailAddressFixture.OTHER_AT_JAMES.asString())};
        Address[] invalid = {};
        SendFailedException sfe = new SendFailedException("Message",
            new Exception(),
            validSent,
            validUnsent,
            invalid);

        UnmodifiableIterator<HostAddress> dnsEntries = ImmutableList.of(
            HOST_ADDRESS_1,
            HOST_ADDRESS_2).iterator();
        when(dnsHelper.retrieveHostAddressIterator(MailAddressFixture.JAMES_APACHE_ORG, false)).thenReturn(dnsEntries);
        when(mailDelivrerToHost.tryDeliveryToHost(any(Mail.class), any(Collection.class), any(HostAddress.class)))
            .thenThrow(sfe);
        ExecutionResult executionResult = testee.deliver(mail);

        verify(mailDelivrerToHost, times(2)).tryDeliveryToHost(any(Mail.class), any(Collection.class), any(HostAddress.class));
        assertThat(executionResult.getExecutionState()).isEqualTo(ExecutionResult.ExecutionState.TEMPORARY_FAILURE);
    }

    @Test
    void deliverShouldWorkIfOnlyMX2Valid() throws Exception {
        Mail mail = FakeMail.builder().name("name").recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES).build();
        Address[] validSent = {};
        Address[] validUnsent = {new InternetAddress(MailAddressFixture.OTHER_AT_JAMES.asString())};
        Address[] invalid = {};
        SendFailedException sfe = new SendFailedException("Message",
            new Exception(),
            validSent,
            validUnsent,
            invalid);

        UnmodifiableIterator<HostAddress> dnsEntries = ImmutableList.of(
            HOST_ADDRESS_1,
            HOST_ADDRESS_2).iterator();
        when(dnsHelper.retrieveHostAddressIterator(MailAddressFixture.JAMES_APACHE_ORG, false)).thenReturn(dnsEntries);
        when(mailDelivrerToHost.tryDeliveryToHost(any(Mail.class), any(Collection.class), eq(HOST_ADDRESS_1)))
            .thenThrow(sfe);
        when(mailDelivrerToHost.tryDeliveryToHost(any(Mail.class), any(Collection.class), eq(HOST_ADDRESS_2)))
            .thenReturn(ExecutionResult.success());
        ExecutionResult executionResult = testee.deliver(mail);

        verify(mailDelivrerToHost, times(2)).tryDeliveryToHost(any(Mail.class), any(Collection.class), any(HostAddress.class));
        assertThat(executionResult.getExecutionState()).isEqualTo(ExecutionResult.ExecutionState.SUCCESS);
    }

}
