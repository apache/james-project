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

package org.apache.james.transport.mailets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;

import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.transport.util.MimeMessageBodyGenerator;
import org.apache.james.util.date.ZonedDateTimeProvider;
import org.apache.james.vacation.api.AccountId;
import org.apache.james.vacation.api.RecipientId;
import org.apache.james.vacation.api.Vacation;
import org.apache.james.vacation.api.VacationRepository;
import org.apache.james.vacation.api.VacationService;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.AutomaticallySentMailDetector;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

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
    private VacationService vacationService;
    private ZonedDateTimeProvider zonedDateTimeProvider;
    private MimeMessageBodyGenerator mimeMessageBodyGenerator;
    private MailetContext mailetContext;
    private MailAddress originalSender;
    private MailAddress originalRecipient;
    private AutomaticallySentMailDetector automaticallySentMailDetector;
    private RecipientId recipientId;
    private FakeMail mail;

    @Before
    public void setUp() throws Exception {
        originalSender = new MailAddress("distant@apache.org");
        originalRecipient = new MailAddress(USERNAME);
        recipientId = RecipientId.fromMailAddress(originalSender);
        mail = FakeMail.builder()
            .name("name")
            .fileName("spamMail.eml")
            .recipient(originalRecipient)
            .sender(originalSender)
            .build();

        mimeMessageBodyGenerator = mock(MimeMessageBodyGenerator.class);
        vacationService = mock(VacationService.class);
        zonedDateTimeProvider = mock(ZonedDateTimeProvider.class);
        automaticallySentMailDetector = mock(AutomaticallySentMailDetector.class);
        testee = new VacationMailet(vacationService, zonedDateTimeProvider, automaticallySentMailDetector, mimeMessageBodyGenerator);
        mailetContext = mock(MailetContext.class);
        testee.init(FakeMailetConfig.builder()
                .mailetName("vacation")
                .mailetContext(mailetContext)
                .build());
    }

    @Test
    public void unactivatedVacationShouldNotSendNotification() throws Exception {
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2017);
        when(vacationService.retrieveVacation(AccountId.fromString(USERNAME)))
            .thenReturn(Mono.just(VacationRepository.DEFAULT_VACATION));
        when(automaticallySentMailDetector.isAutomaticallySent(mail)).thenReturn(false);

        testee.service(mail);

        verifyNoMoreInteractions(mailetContext);
    }

    @Test
    public void activateVacationShouldSendNotification() throws Exception {
        when(vacationService.retrieveVacation(AccountId.fromString(USERNAME)))
            .thenReturn(Mono.just(VACATION));
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2017);
        when(automaticallySentMailDetector.isAutomaticallySent(mail)).thenReturn(false);
        when(vacationService.isNotificationRegistered(ACCOUNT_ID, recipientId)).thenReturn(Mono.just(false));

        testee.service(mail);

        verify(mailetContext).sendMail(eq(MailAddress.nullSender()), eq(ImmutableList.of(originalSender)), any());
        verify(vacationService).retrieveVacation(AccountId.fromString(USERNAME));
        verify(vacationService).isNotificationRegistered(ACCOUNT_ID, recipientId);
        verify(vacationService).registerNotification(ACCOUNT_ID, recipientId, Optional.of(DATE_TIME_2018));
        verifyNoMoreInteractions(mailetContext, vacationService);
    }

    @Test
    public void activateVacationShouldNotSendNotificationIfAlreadySent() throws Exception {
        when(vacationService.retrieveVacation(AccountId.fromString(USERNAME)))
            .thenReturn(Mono.just(VACATION));
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2017);
        when(vacationService.isNotificationRegistered(ACCOUNT_ID, recipientId)).thenReturn(Mono.just(true));

        testee.service(mail);

        verifyNoMoreInteractions(mailetContext);
    }

    @Test
    public void activateVacationShouldNotSendNotificationIfSenderSendsMailToHimself() throws Exception {
        MailAddress sender = new MailAddress(USERNAME);
        recipientId = RecipientId.fromMailAddress(sender);
        when(vacationService.retrieveVacation(AccountId.fromString(USERNAME)))
            .thenReturn(Mono.just(VACATION));
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2017);
        when(automaticallySentMailDetector.isAutomaticallySent(mail)).thenReturn(false);
        when(vacationService.isNotificationRegistered(ACCOUNT_ID, recipientId)).thenReturn(Mono.just(false));

        mail = FakeMail.builder()
            .name("name")
            .fileName("spamMail.eml")
            .sender(sender)
            .recipient(sender)
            .build();
        testee.service(mail);

        verifyNoMoreInteractions(mailetContext);
    }

    @Test
    public void activateVacationShouldNotSendNotificationIfNoReplySender() throws Exception {
        MailAddress noReplySender = new MailAddress("james-noreply@apache.org");
        recipientId = RecipientId.fromMailAddress(noReplySender);
        when(vacationService.retrieveVacation(AccountId.fromString(USERNAME)))
            .thenReturn(Mono.just(VACATION));
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2017);
        when(automaticallySentMailDetector.isAutomaticallySent(mail)).thenReturn(false);
        when(vacationService.isNotificationRegistered(ACCOUNT_ID, recipientId)).thenReturn(Mono.just(false));

        mail = FakeMail.builder()
            .name("name")
            .fileName("spamMail.eml")
            .sender(noReplySender)
            .recipient(new MailAddress(USERNAME))
            .build();
        testee.service(mail);

        verifyNoMoreInteractions(mailetContext, vacationService);
    }
    
    @Test
    public void activateVacationShouldSendNotificationIfErrorUpdatingNotificationRepository() throws Exception {
        when(vacationService.retrieveVacation(AccountId.fromString(USERNAME)))
            .thenReturn(Mono.just(VACATION));
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2017);
        RecipientId recipientId = RecipientId.fromMailAddress(originalSender);
        when(vacationService.isNotificationRegistered(ACCOUNT_ID, recipientId)).thenReturn(Mono.just(false));
        when(vacationService.registerNotification(ACCOUNT_ID, recipientId, Optional.of(DATE_TIME_2018))).thenThrow(new RuntimeException());

        testee.service(mail);

        verify(mailetContext).sendMail(eq(MailAddress.nullSender()), eq(ImmutableList.of(originalSender)), any());
        verifyNoMoreInteractions(mailetContext);
    }

    @Test
    public void activateVacationShouldSendNotificationIfErrorRetrievingNotificationRepository() throws Exception {
        when(vacationService.retrieveVacation(AccountId.fromString(USERNAME)))
            .thenReturn(Mono.just(VACATION));
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2017);
        RecipientId recipientId = RecipientId.fromMailAddress(originalSender);
        when(vacationService.isNotificationRegistered(ACCOUNT_ID, recipientId)).thenThrow(new RuntimeException());

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
            .name("name")
            .fileName("spamMail.eml")
            .recipients(originalRecipient, secondRecipient)
            .sender(originalSender)
            .build();
        when(vacationService.retrieveVacation(AccountId.fromString(USERNAME)))
            .thenReturn(Mono.just(VACATION));
        when(vacationService.retrieveVacation(secondAccountId))
            .thenReturn(Mono.just(VACATION));
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2017);
        when(automaticallySentMailDetector.isAutomaticallySent(mail)).thenReturn(false);
        when(vacationService.isNotificationRegistered(ACCOUNT_ID, RecipientId.fromMailAddress(originalSender)))
            .thenReturn(Mono.just(false));
        when(vacationService.isNotificationRegistered(secondAccountId, RecipientId.fromMailAddress(originalSender)))
            .thenReturn(Mono.just(false));
        when(vacationService.registerNotification(any(), any(), any()))
            .thenReturn(Mono.empty());

        testee.service(mail);

        verify(mailetContext, times(2)).sendMail(eq(MailAddress.nullSender()), eq(ImmutableList.of(originalSender)), any());
        verifyNoMoreInteractions(mailetContext);
    }

    @Test
    public void serviceShouldNotSendNotificationUponErrorsRetrievingVacationObject() throws Exception {
        when(vacationService.retrieveVacation(AccountId.fromString(USERNAME)))
            .thenReturn(Mono.error(new RuntimeException()));
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2017);
        when(automaticallySentMailDetector.isAutomaticallySent(mail)).thenReturn(false);

        testee.service(mail);

        verifyNoMoreInteractions(mailetContext);
    }

    @Test
    public void serviceShouldNotSendNotificationUponErrorsDetectingAutomaticallySentMails() throws Exception {
        when(vacationService.retrieveVacation(AccountId.fromString(USERNAME)))
            .thenReturn(Mono.just(VACATION));
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2017);
        when(automaticallySentMailDetector.isAutomaticallySent(mail)).thenThrow(new MessagingException());

        testee.service(mail);

        verifyNoMoreInteractions(mailetContext);
    }

    @Test
    public void serviceShouldNotSendNotificationToSenderWithNoReplySuffix() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("name")
            .fileName("spamMail.eml")
            .recipient(originalRecipient)
            .sender(new MailAddress("distant-noreply@apache.org"))
            .build();

        when(vacationService.retrieveVacation(AccountId.fromString(USERNAME)))
            .thenReturn(Mono.just(VACATION));
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2017);
        when(vacationService.isNotificationRegistered(any(), any())).thenReturn(Mono.just(false));
        when(automaticallySentMailDetector.isAutomaticallySent(mail)).thenReturn(false);

        testee.service(mail);

        verifyNoMoreInteractions(mailetContext);
    }

    @Test
    public void serviceShouldNotSendNotificationToEmptyReplyTo() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("name")
            .fileName("noReplyTo.eml")
            .recipient(originalRecipient)
            .sender(new MailAddress("distant-noreply@apache.org"))
            .build();

        when(vacationService.retrieveVacation(AccountId.fromString(USERNAME)))
            .thenReturn(Mono.just(VACATION));
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2017);
        when(vacationService.isNotificationRegistered(any(), any())).thenReturn(Mono.just(false));
        when(automaticallySentMailDetector.isAutomaticallySent(mail)).thenReturn(false);

        testee.service(mail);

        verifyNoMoreInteractions(mailetContext);
    }

    @Test
    public void serviceShouldNotPropagateExceptionIfSendFails() throws Exception {
        when(vacationService.retrieveVacation(AccountId.fromString(USERNAME)))
            .thenReturn(Mono.just(VACATION));
        when(zonedDateTimeProvider.get()).thenReturn(DATE_TIME_2017);
        when(automaticallySentMailDetector.isAutomaticallySent(mail)).thenReturn(false);
        when(vacationService.isNotificationRegistered(ACCOUNT_ID, RecipientId.fromMailAddress(originalSender)))
            .thenReturn(Mono.just(false));

        doThrow(new MessagingException()).when(mailetContext).sendMail(eq(originalSender), eq(ImmutableList.of(originalRecipient)), any());

        testee.service(mail);

        verify(mailetContext).sendMail(eq(MailAddress.nullSender()), eq(ImmutableList.of(originalSender)), any());
        verifyNoMoreInteractions(mailetContext);
    }

}
