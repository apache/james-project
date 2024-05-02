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

package org.apache.james;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.vacation.api.AccountId;
import org.apache.james.vacation.api.NotificationRegistry;
import org.apache.james.vacation.api.RecipientId;
import org.apache.james.vacation.api.Vacation;
import org.apache.james.vacation.api.VacationPatch;
import org.apache.james.vacation.api.VacationRepository;
import org.junit.Before;
import org.junit.Test;

import reactor.core.publisher.Mono;

public class DefaultVacationServiceTest {

    public static final ZonedDateTime DATE_TIME_2016 = ZonedDateTime.parse("2016-10-09T08:07:06+07:00[Asia/Vientiane]");
    public static final ZonedDateTime DATE_TIME_2018 = ZonedDateTime.parse("2018-10-09T08:07:06+07:00[Asia/Vientiane]");
    public static final String USERNAME = "benwa@apache.org";
    public static final AccountId ACCOUNT_ID = AccountId.fromString(USERNAME);
    public static final Vacation VACATION = Vacation.builder()
        .enabled(true)
        .fromDate(Optional.of(DATE_TIME_2016))
        .toDate(Optional.of(DATE_TIME_2018))
        .textBody("Explaining my vacation")
        .build();

    private RecipientId recipientId;
    private DefaultVacationService testee;
    private VacationRepository vacationRepository;
    private NotificationRegistry notificationRegistry;

    @Before
    public void setUp() throws Exception {
        recipientId = RecipientId.fromMailAddress(new MailAddress("distant@apache.org"));

        vacationRepository = mock(VacationRepository.class);
        notificationRegistry = mock(NotificationRegistry.class);
        testee = new DefaultVacationService(vacationRepository, notificationRegistry);
    }

    @Test
    public void modifyVacationShouldFlush() {
        VacationPatch vacationPatch = VacationPatch.builderFrom(VACATION).build();

        when(vacationRepository.modifyVacation(ACCOUNT_ID, vacationPatch)).thenReturn(Mono.empty());
        when(notificationRegistry.flush(ACCOUNT_ID)).thenReturn(Mono.empty());

        testee.modifyVacation(ACCOUNT_ID, vacationPatch).block();

        verify(vacationRepository).modifyVacation(ACCOUNT_ID, vacationPatch);
        verify(notificationRegistry).flush(ACCOUNT_ID);
        verifyNoMoreInteractions(vacationRepository, notificationRegistry);
    }

    @Test
    public void retrieveVacationShouldDelegate() {
        when(vacationRepository.retrieveVacation(ACCOUNT_ID)).thenReturn(Mono.just(VACATION));

        Vacation vacation = testee.retrieveVacation(ACCOUNT_ID).block();
        assertThat(vacation).isEqualTo(VACATION);

        verify(vacationRepository).retrieveVacation(ACCOUNT_ID);
        verifyNoMoreInteractions(vacationRepository, notificationRegistry);
    }

    @Test
    public void registerNotificationShouldDelegate() {
        when(notificationRegistry.register(ACCOUNT_ID, recipientId, Optional.of(DATE_TIME_2018))).thenReturn(Mono.empty());

        testee.registerNotification(ACCOUNT_ID, recipientId, Optional.of(DATE_TIME_2018)).block();

        verify(notificationRegistry).register(ACCOUNT_ID, recipientId, Optional.of(DATE_TIME_2018));
        verifyNoMoreInteractions(vacationRepository, notificationRegistry);
    }

    @Test
    public void risNotificationRegisteredShouldDelegate() {
        when(notificationRegistry.isRegistered(ACCOUNT_ID, recipientId)).thenReturn(Mono.just(true));

        Boolean result = testee.isNotificationRegistered(ACCOUNT_ID, recipientId).block();
        assertThat(result).isTrue();

        verify(notificationRegistry).isRegistered(ACCOUNT_ID, recipientId);
        verifyNoMoreInteractions(vacationRepository, notificationRegistry);
    }

    @Test
    public void flushNotificationsShouldDelegate() {
        when(notificationRegistry.flush(ACCOUNT_ID)).thenReturn(Mono.empty());

        testee.flushNotifications(ACCOUNT_ID).block();

        verify(notificationRegistry).flush(ACCOUNT_ID);
        verifyNoMoreInteractions(vacationRepository, notificationRegistry);
    }
}
