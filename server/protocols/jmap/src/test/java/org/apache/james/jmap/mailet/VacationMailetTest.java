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

package org.apache.james.jmap.mailet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.jmap.api.vacation.AccountId;
import org.apache.james.jmap.api.vacation.NotificationRegistry;
import org.apache.james.jmap.api.vacation.RecipientId;
import org.apache.james.jmap.api.vacation.Vacation;
import org.apache.james.jmap.api.vacation.VacationRepository;
import org.apache.james.jmap.utils.MimeMessageBodyGenerator;
import org.apache.james.util.date.ZonedDateTimeProvider;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.AutomaticallySentMailDetector;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class VacationMailetTest {

    public static final ZonedDateTime DATE_TIME_2016 = ZonedDateTime.parse("2016-10-09T08:07:06+07:00[Asia/Vientiane]");
    public static final ZonedDateTime DATE_TIME_2017 = ZonedDateTime.parse("2017-10-09T08:07:06+07:00[Asia/Vientiane]");
    public static final ZonedDateTime DATE_TIME_2018 = ZonedDateTime.parse("2018-10-09T08:07:06+07:00[Asia/Vientiane]");
    public static final String USERNAME = "benwa@apache.org";
    public static final AccountId ACCOUNT_ID = AccountId.fromString(USERNAME);
    public static final Vacation VACATION = Vacation.builder()
        .enabled(true)
        .fromDate(Optional.of(DATE_TIME_2016))
        .toDate(Optional.of(DATE_TIME_2018))
        .textBody("Explaining my vacation")
        .build();

    private VacationMailet testee;
    private VacationRepository vacationRepository;
    private ZonedDateTimeProvider zonedDateTimeProvider;
    private MimeMessageBodyGenerator mimeMessageBodyGenerator;
    private MailetContext mailetContext;
    private MailAddress originalSender;
    private MailAddress originalRecipient;
    private AutomaticallySentMailDetector automaticallySentMailDetector;
    private NotificationRegistry notificationRegistry;
    private RecipientId recipientId;
    private FakeMail mail;

    @Before
    public void setUp() throws Exception {
        originalSender = new MailAddress("distant@apache.org");
        originalRecipient = new MailAddress(USERNAME);
        recipientId = RecipientId.fromMailAddress(originalSender);
        mail = FakeMail.builder()
            .fileName("spamMail.eml")
            .recipient(originalRecipient)
            .sender(originalSender)
            .build();

        mimeMessageBodyGenerator = mock(MimeMessageBodyGenerator.class);
        vacationRepository = mock(VacationRepository.class);
        zonedDateTimeProvider = mock(ZonedDateTimeProvider.class);
        automaticallySentMailDetector = mock(AutomaticallySentMailDetector.class);
        notificationRegistry = mock(NotificationRegistry.class);
        testee = new VacationMailet(vacationRepository, zonedDateTimeProvider, automaticallySentMailDetector, notificationRegistry, mimeMessageBodyGenerator);
        mailetContext = mock(MailetContext.class);
        testee.init(FakeMailetConfig.builder()
                .mailetName("vacation")
                .mailetContext(mailetContext)
                .build());
    }

    @Test
    public void unactivatedVacationShouldNotSendNotification() throws Exception {
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2017);
        when(vacationRepository.retrieveVacation(AccountId.fromString(USERNAME)))
            .thenReturn(CompletableFuture.completedFuture(VacationRepository.DEFAULT_VACATION));
        when(automaticallySentMailDetector.isAutomaticallySent(mail)).thenReturn(false);

        testee.service(mail);

        verifyNoMoreInteractions(mailetContext);
    }

    @Test
    public void activateVacationShouldSendNotification() throws Exception {
        when(vacationRepository.retrieveVacation(AccountId.fromString(USERNAME)))
            .thenReturn(CompletableFuture.completedFuture(VACATION));
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2017);
        when(automaticallySentMailDetector.isAutomaticallySent(mail)).thenReturn(false);
        when(notificationRegistry.isRegistered(ACCOUNT_ID, recipientId))
            .thenReturn(CompletableFuture.completedFuture(false));

        testee.service(mail);

        verify(mailetContext).sendMail(eq(originalRecipient), eq(ImmutableList.of(originalSender)), any());
        verify(notificationRegistry).isRegistered(ACCOUNT_ID, recipientId);
        verify(notificationRegistry).register(ACCOUNT_ID, recipientId, Optional.of(DATE_TIME_2018));
        verifyNoMoreInteractions(mailetContext, notificationRegistry);
    }

    @Test
    public void activateVacationShouldNotSendNotificationIfAlreadySent() throws Exception {
        when(vacationRepository.retrieveVacation(AccountId.fromString(USERNAME)))
            .thenReturn(CompletableFuture.completedFuture(VACATION));
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2017);
        when(notificationRegistry.isRegistered(ACCOUNT_ID, recipientId))
            .thenReturn(CompletableFuture.completedFuture(true));

        testee.service(mail);

        verifyNoMoreInteractions(mailetContext);
    }

    @Test
    public void activateVacationShouldSendNotificationIfErrorUpdatingNotificationRepository() throws Exception {
        when(vacationRepository.retrieveVacation(AccountId.fromString(USERNAME)))
            .thenReturn(CompletableFuture.completedFuture(VACATION));
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2017);
        RecipientId recipientId = RecipientId.fromMailAddress(originalSender);
        when(notificationRegistry.isRegistered(ACCOUNT_ID, recipientId))
            .thenReturn(CompletableFuture.completedFuture(false));
        when(notificationRegistry.register(ACCOUNT_ID, recipientId, Optional.of(DATE_TIME_2018))).thenThrow(new RuntimeException());

        testee.service(mail);

        verify(mailetContext).sendMail(eq(originalRecipient), eq(ImmutableList.of(originalSender)), any());
        verifyNoMoreInteractions(mailetContext);
    }

    @Test
    public void activateVacationShouldSendNotificationIfErrorRetrievingNotificationRepository() throws Exception {
        when(vacationRepository.retrieveVacation(AccountId.fromString(USERNAME)))
            .thenReturn(CompletableFuture.completedFuture(VACATION));
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2017);
        RecipientId recipientId = RecipientId.fromMailAddress(originalSender);
        when(notificationRegistry.isRegistered(ACCOUNT_ID, recipientId))
            .thenThrow(new RuntimeException());

        testee.service(mail);

        verifyNoMoreInteractions(mailetContext);
    }

    @Test
    public void activateVacationShouldNotSendNotificationToMailingList() throws Exception {
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2017);
        when(automaticallySentMailDetector.isAutomaticallySent(mail)).thenReturn(true);

        testee.service(mail);

        verifyNoMoreInteractions(mailetContext);
    }

    @Test
    public void multipleRecipientShouldGenerateNotifications() throws MessagingException {
        String secondUserName = "default@any.com";
        MailAddress secondRecipient = new MailAddress(secondUserName);
        AccountId secondAccountId = AccountId.fromString(secondUserName);

        FakeMail mail = FakeMail.builder()
            .fileName("spamMail.eml")
            .recipients(originalRecipient, secondRecipient)
            .sender(originalSender)
            .build();
        when(vacationRepository.retrieveVacation(AccountId.fromString(USERNAME)))
            .thenReturn(CompletableFuture.completedFuture(VACATION));
        when(vacationRepository.retrieveVacation(secondAccountId))
            .thenReturn(CompletableFuture.completedFuture(VACATION));
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2017);
        when(automaticallySentMailDetector.isAutomaticallySent(mail)).thenReturn(false);
        when(notificationRegistry.isRegistered(ACCOUNT_ID, RecipientId.fromMailAddress(originalSender)))
            .thenReturn(CompletableFuture.completedFuture(false));
        when(notificationRegistry.isRegistered(secondAccountId, RecipientId.fromMailAddress(originalSender)))
            .thenReturn(CompletableFuture.completedFuture(false));
        when(notificationRegistry.register(any(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        testee.service(mail);

        verify(mailetContext).sendMail(eq(originalRecipient), eq(ImmutableList.of(originalSender)), any());
        verify(mailetContext).sendMail(eq(secondRecipient), eq(ImmutableList.of(originalSender)), any());
        verifyNoMoreInteractions(mailetContext);
    }

    @Test
    public void serviceShouldNotSendNotificationUponErrorsRetrievingVacationObject() throws Exception {
        when(vacationRepository.retrieveVacation(AccountId.fromString(USERNAME)))
            .thenReturn(CompletableFuture.supplyAsync(() -> {
                throw new RuntimeException();
            }));
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2017);
        when(automaticallySentMailDetector.isAutomaticallySent(mail)).thenReturn(false);

        testee.service(mail);

        verifyNoMoreInteractions(mailetContext);
    }

    @Test
    public void serviceShouldNotSendNotificationUponErrorsDetectingAutomaticallySentMails() throws Exception {
        when(vacationRepository.retrieveVacation(AccountId.fromString(USERNAME)))
            .thenReturn(CompletableFuture.completedFuture(VACATION));
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2017);
        when(automaticallySentMailDetector.isAutomaticallySent(mail)).thenThrow(new MessagingException());

        testee.service(mail);

        verifyNoMoreInteractions(mailetContext);
    }

    @Test
    public void serviceShouldNotPropagateExceptionIfSendFails() throws Exception {
        when(vacationRepository.retrieveVacation(AccountId.fromString(USERNAME)))
            .thenReturn(CompletableFuture.completedFuture(VACATION));
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2017);
        when(automaticallySentMailDetector.isAutomaticallySent(mail)).thenReturn(false);
        when(notificationRegistry.isRegistered(ACCOUNT_ID, RecipientId.fromMailAddress(originalSender)))
            .thenReturn(CompletableFuture.completedFuture(false));

        doThrow(new MessagingException()).when(mailetContext).sendMail(eq(originalSender), eq(ImmutableList.of(originalRecipient)), any());

        testee.service(mail);

        verify(mailetContext).sendMail(eq(originalRecipient), eq(ImmutableList.of(originalSender)), any());
        verifyNoMoreInteractions(mailetContext);
    }

}
