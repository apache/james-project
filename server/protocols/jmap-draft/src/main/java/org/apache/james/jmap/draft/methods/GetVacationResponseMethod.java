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

package org.apache.james.jmap.draft.methods;

import static org.apache.james.jmap.http.LoggingHelper.jmapAction;
import static org.apache.james.jmap.utils.AccountIdUtil.toVacationAccountId;

import jakarta.inject.Inject;

import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.jmap.draft.model.GetVacationRequest;
import org.apache.james.jmap.draft.model.GetVacationResponse;
import org.apache.james.jmap.draft.model.VacationResponse;
import org.apache.james.jmap.methods.JmapRequest;
import org.apache.james.jmap.methods.JmapResponse;
import org.apache.james.jmap.methods.Method;
import org.apache.james.jmap.model.MethodCallId;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.date.ZonedDateTimeProvider;
import org.apache.james.vacation.api.Vacation;
import org.apache.james.vacation.api.VacationService;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class GetVacationResponseMethod implements Method {

    public static final Request.Name METHOD_NAME = Request.name("getVacationResponse");
    public static final Response.Name RESPONSE_NAME = Method.Response.name("vacationResponse");

    private final VacationService vacationService;
    private final ZonedDateTimeProvider zonedDateTimeProvider;
    private final MetricFactory metricFactory;

    @Inject
    public GetVacationResponseMethod(VacationService vacationService, ZonedDateTimeProvider zonedDateTimeProvider, MetricFactory metricFactory) {
        this.vacationService = vacationService;
        this.zonedDateTimeProvider = zonedDateTimeProvider;
        this.metricFactory = metricFactory;
    }

    @Override
    public Request.Name requestHandled() {
        return METHOD_NAME;
    }

    @Override
    public Class<? extends JmapRequest> requestType() {
        return GetVacationRequest.class;
    }

    @Override
    public Flux<JmapResponse> process(JmapRequest request, MethodCallId methodCallId, MailboxSession mailboxSession) {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(methodCallId);
        Preconditions.checkNotNull(mailboxSession);
        Preconditions.checkArgument(request instanceof GetVacationRequest);

        return Flux.from(metricFactory.decoratePublisherWithTimerMetric(JMAP_PREFIX + METHOD_NAME.getName(),
            process(mailboxSession)
                .map(response -> JmapResponse.builder()
                    .methodCallId(methodCallId)
                    .responseName(RESPONSE_NAME)
                    .response(response)
                    .build())
                .flux()))
            .contextWrite(jmapAction("VACATION"));
    }

    private Mono<GetVacationResponse> process(MailboxSession mailboxSession) {
        return vacationService.retrieveVacation(toVacationAccountId(AccountId.fromUsername(mailboxSession.getUser())))
            .map(vacation -> asVacationResponse(mailboxSession, vacation));
    }

    private GetVacationResponse asVacationResponse(MailboxSession mailboxSession, Vacation vacation) {
        return GetVacationResponse.builder()
            .accountId(mailboxSession.getUser().asString())
            .vacationResponse(VacationResponse.builder()
                .fromVacation(vacation)
                .activated(vacation.isActiveAtDate(zonedDateTimeProvider.get()))
                .build())
            .build();
    }
}
