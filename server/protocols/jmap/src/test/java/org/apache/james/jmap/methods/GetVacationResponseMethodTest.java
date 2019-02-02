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

import org.apache.james.core.User;
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
import org.apache.james.metrics.logger.DefaultMetricFactory;
import org.apache.james.util.date.ZonedDateTimeProvider;
import org.junit.Before;
import org.junit.Test;

public class GetVacationResponseMethodTest {

    private static final ZonedDateTime DATE_2014 = ZonedDateTime.parse("2014-09-30T14:10:00Z");
    private static final ZonedDateTime DATE_2015 = ZonedDateTime.parse("2015-09-30T14:10:00Z");
    private static final ZonedDateTime DATE_2016 = ZonedDateTime.parse("2016-09-30T14:10:00Z");

    public static final String USERNAME = "username";
    private GetVacationResponseMethod testee;
    private VacationRepository vacationRepository;
    private MailboxSession mailboxSession;
    private User user;
    private ZonedDateTimeProvider zonedDateTimeProvider;

    @Before
    public void setUp() {
        zonedDateTimeProvider = mock(ZonedDateTimeProvider.class);
        vacationRepository = mock(VacationRepository.class);
        mailboxSession = mock(MailboxSession.class);
        user = User.fromUsername(USERNAME);
        testee = new GetVacationResponseMethod(vacationRepository, zonedDateTimeProvider, new DefaultMetricFactory());

        when(zonedDateTimeProvider.get()).thenReturn(DATE_2014);
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
            .fromDate(Optional.of(DATE_2014))
            .toDate(Optional.of(DATE_2016))
            .build();
        when(vacationRepository.retrieveVacation(AccountId.fromString(USERNAME))).thenReturn(CompletableFuture.completedFuture(vacation));
        when(mailboxSession.getUser()).thenReturn(user);
        when(zonedDateTimeProvider.get()).thenReturn(DATE_2015);

        GetVacationRequest getVacationRequest = GetVacationRequest.builder().build();

        Stream<JmapResponse> result = testee.process(getVacationRequest, clientId, mailboxSession);

        JmapResponse expected = JmapResponse.builder()
            .clientId(clientId)
            .responseName(GetVacationResponseMethod.RESPONSE_NAME)
            .response(GetVacationResponse.builder()
                .accountId(USERNAME)
                .vacationResponse(VacationResponse.builder()
                    .fromVacation(vacation)
                    .activated(true)
                    .build())
                .build())
            .build();
        assertThat(result).containsExactly(expected);
    }

    @Test
    public void processShouldReturnUnActivatedVacationResponseWhenBeforeDate() {
        ClientId clientId = mock(ClientId.class);
        Vacation vacation = Vacation.builder()
            .enabled(true)
            .textBody("I am in vacation")
            .subject(Optional.of("subject"))
            .fromDate(Optional.of(DATE_2015))
            .toDate(Optional.of(DATE_2016))
            .build();
        when(vacationRepository.retrieveVacation(AccountId.fromString(USERNAME))).thenReturn(CompletableFuture.completedFuture(vacation));
        when(mailboxSession.getUser()).thenReturn(user);
        when(zonedDateTimeProvider.get()).thenReturn(DATE_2014);

        GetVacationRequest getVacationRequest = GetVacationRequest.builder().build();

        Stream<JmapResponse> result = testee.process(getVacationRequest, clientId, mailboxSession);

        JmapResponse expected = JmapResponse.builder()
            .clientId(clientId)
            .responseName(GetVacationResponseMethod.RESPONSE_NAME)
            .response(GetVacationResponse.builder()
                .accountId(USERNAME)
                .vacationResponse(VacationResponse.builder()
                    .fromVacation(vacation)
                    .activated(false)
                    .build())
                .build())
            .build();
        assertThat(result).containsExactly(expected);
    }



    @Test
    public void processShouldReturnUnActivatedVacationResponseWhenAfterDate() {
        ClientId clientId = mock(ClientId.class);
        Vacation vacation = Vacation.builder()
            .enabled(true)
            .textBody("I am in vacation")
            .subject(Optional.of("subject"))
            .fromDate(Optional.of(DATE_2014))
            .toDate(Optional.of(DATE_2015))
            .build();
        when(vacationRepository.retrieveVacation(AccountId.fromString(USERNAME))).thenReturn(CompletableFuture.completedFuture(vacation));
        when(mailboxSession.getUser()).thenReturn(user);
        when(zonedDateTimeProvider.get()).thenReturn(DATE_2016);

        GetVacationRequest getVacationRequest = GetVacationRequest.builder().build();

        Stream<JmapResponse> result = testee.process(getVacationRequest, clientId, mailboxSession);

        JmapResponse expected = JmapResponse.builder()
            .clientId(clientId)
            .responseName(GetVacationResponseMethod.RESPONSE_NAME)
            .response(GetVacationResponse.builder()
                .accountId(USERNAME)
                .vacationResponse(VacationResponse.builder()
                    .fromVacation(vacation)
                    .activated(false)
                    .build())
                .build())
            .build();
        assertThat(result).containsExactly(expected);
    }

}
