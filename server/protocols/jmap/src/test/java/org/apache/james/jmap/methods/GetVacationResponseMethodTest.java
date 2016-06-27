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

package org.apache.james.jmap.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.apache.james.jmap.api.vacation.AccountId;
import org.apache.james.jmap.api.vacation.Vacation;
import org.apache.james.jmap.api.vacation.VacationRepository;
import org.apache.james.jmap.model.ClientId;
import org.apache.james.jmap.model.GetMailboxesRequest;
import org.apache.james.jmap.model.GetVacationRequest;
import org.apache.james.jmap.model.GetVacationResponse;
import org.apache.james.jmap.model.SetMailboxesRequest;
import org.apache.james.jmap.model.VacationResponse;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.util.date.ZonedDateTimeProvider;
import org.junit.Before;
import org.junit.Test;

public class GetVacationResponseMethodTest {

    public static final String USERNAME = "username";
    public static final ZonedDateTime ZONED_DATE_TIME_2014 = ZonedDateTime.parse("2014-09-30T14:10:00Z");
    public static final ZonedDateTime ZONED_DATE_TIME_2015 = ZonedDateTime.parse("2015-09-30T14:10:00Z");
    public static final ZonedDateTime ZONED_DATE_TIME_2016 = ZonedDateTime.parse("2016-09-30T14:10:00Z");
    private GetVacationResponseMethod testee;
    private VacationRepository vacationRepository;
    private MailboxSession mailboxSession;
    private MailboxSession.User user;
    private ZonedDateTimeProvider zonedDateTimeProvider;

    @Before
    public void setUp() {
        vacationRepository = mock(VacationRepository.class);
        mailboxSession = mock(MailboxSession.class);
        user = mock(MailboxSession.User.class);
        zonedDateTimeProvider = mock(ZonedDateTimeProvider.class);
        testee = new GetVacationResponseMethod(vacationRepository, zonedDateTimeProvider);
    }

    @Test(expected = NullPointerException.class)
    public void processShouldThrowOnNullRequest() {
        testee.process(null, mock(ClientId.class), mock(MailboxSession.class));
    }

    @Test(expected = NullPointerException.class)
    public void processShouldThrowOnNullClientId() {
        testee.process(mock(GetMailboxesRequest.class), null, mock(MailboxSession.class));
    }

    @Test(expected = NullPointerException.class)
    public void processShouldThrowOnNullMailboxSession() {
        testee.process(mock(GetMailboxesRequest.class), mock(ClientId.class), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void processShouldThrowOnWrongRequestType() {
        testee.process(mock(SetMailboxesRequest.class), mock(ClientId.class), mock(MailboxSession.class));
    }

    @Test
    public void processShouldReturnTheAppropriateVacationResponse() {
        ClientId clientId = mock(ClientId.class);
        Vacation vacation = Vacation.builder()
            .enabled(true)
            .textBody("I am in vacation")
            .subject(Optional.of("subject"))
            .build();
        when(vacationRepository.retrieveVacation(AccountId.fromString(USERNAME))).thenReturn(CompletableFuture.completedFuture(vacation));
        when(mailboxSession.getUser()).thenReturn(user);
        when(user.getUserName()).thenReturn(USERNAME);
        when(zonedDateTimeProvider.get()).thenReturn(ZONED_DATE_TIME_2015);

        GetVacationRequest getVacationRequest = GetVacationRequest.builder().build();

        Stream<JmapResponse> result = testee.process(getVacationRequest, clientId, mailboxSession);

        JmapResponse expected = JmapResponse.builder()
            .clientId(clientId)
            .responseName(GetVacationResponseMethod.RESPONSE_NAME)
            .response(GetVacationResponse.builder()
                .accountId(USERNAME)
                .vacationResponse(VacationResponse.builder()
                    .id(Vacation.ID)
                    .enabled(true)
                    .textBody(Optional.of("I am in vacation"))
                    .subject(Optional.of("subject"))
                    .build())
                .build())
            .build();
        assertThat(result).containsExactly(expected);
    }

    @Test
    public void processShouldMarkOutDatedVacationAsUnactivated() {
        ClientId clientId = mock(ClientId.class);
        Vacation vacation = Vacation.builder()
            .enabled(true)
            .textBody("I am in vacation")
            .subject(Optional.of("subject"))
            .fromDate(Optional.of(ZONED_DATE_TIME_2014))
            .toDate(Optional.of(ZONED_DATE_TIME_2015))
            .build();
        when(vacationRepository.retrieveVacation(AccountId.fromString(USERNAME))).thenReturn(CompletableFuture.completedFuture(vacation));
        when(mailboxSession.getUser()).thenReturn(user);
        when(user.getUserName()).thenReturn(USERNAME);
        when(zonedDateTimeProvider.get()).thenReturn(ZONED_DATE_TIME_2016);

        GetVacationRequest getVacationRequest = GetVacationRequest.builder().build();

        Stream<JmapResponse> result = testee.process(getVacationRequest, clientId, mailboxSession);

        JmapResponse expected = JmapResponse.builder()
            .clientId(clientId)
            .responseName(GetVacationResponseMethod.RESPONSE_NAME)
            .response(GetVacationResponse.builder()
                .accountId(USERNAME)
                .vacationResponse(VacationResponse.builder()
                    .enabled(false)
                    .id(Vacation.ID)
                    .textBody(Optional.of("I am in vacation"))
                    .subject(Optional.of("subject"))
                    .fromDate(Optional.of(ZONED_DATE_TIME_2014))
                    .toDate(Optional.of(ZONED_DATE_TIME_2015))
                    .build())
                .build())
            .build();
        assertThat(result).containsExactly(expected);
    }

    @Test
    public void processShouldMarkTooEarlyVacationAsUnactivated() {
        ClientId clientId = mock(ClientId.class);
        Vacation vacation = Vacation.builder()
            .enabled(true)
            .textBody("I am in vacation")
            .subject(Optional.of("subject"))
            .fromDate(Optional.of(ZONED_DATE_TIME_2015))
            .toDate(Optional.of(ZONED_DATE_TIME_2016))
            .build();
        when(vacationRepository.retrieveVacation(AccountId.fromString(USERNAME))).thenReturn(CompletableFuture.completedFuture(vacation));
        when(mailboxSession.getUser()).thenReturn(user);
        when(user.getUserName()).thenReturn(USERNAME);
        when(zonedDateTimeProvider.get()).thenReturn(ZONED_DATE_TIME_2014);

        GetVacationRequest getVacationRequest = GetVacationRequest.builder().build();

        Stream<JmapResponse> result = testee.process(getVacationRequest, clientId, mailboxSession);

        JmapResponse expected = JmapResponse.builder()
            .clientId(clientId)
            .responseName(GetVacationResponseMethod.RESPONSE_NAME)
            .response(GetVacationResponse.builder()
                .accountId(USERNAME)
                .vacationResponse(VacationResponse.builder()
                    .enabled(false)
                    .id(Vacation.ID)
                    .textBody(Optional.of("I am in vacation"))
                    .subject(Optional.of("subject"))
                    .fromDate(Optional.of(ZONED_DATE_TIME_2015))
                    .toDate(Optional.of(ZONED_DATE_TIME_2016))
                    .build())
                .build())
            .build();
        assertThat(result).containsExactly(expected);
    }

}
