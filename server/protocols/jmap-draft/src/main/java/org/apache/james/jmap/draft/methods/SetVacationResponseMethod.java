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

import static org.apache.james.jmap.draft.utils.AccountIdUtil.toVacationAccountId;
import static org.apache.james.jmap.http.LoggingHelper.jmapAction;
import static org.apache.james.util.ReactorUtils.context;

import jakarta.inject.Inject;

import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.jmap.draft.model.MethodCallId;
import org.apache.james.jmap.draft.model.SetError;
import org.apache.james.jmap.draft.model.SetVacationRequest;
import org.apache.james.jmap.draft.model.SetVacationResponse;
import org.apache.james.jmap.draft.model.VacationResponse;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.apache.james.vacation.api.Vacation;
import org.apache.james.vacation.api.VacationService;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SetVacationResponseMethod implements Method {

    public static final Request.Name METHOD_NAME = Request.name("setVacationResponse");
    public static final Response.Name RESPONSE_NAME = Response.name("vacationResponseSet");
    public static final String INVALID_ARGUMENTS = "invalidArguments";
    public static final String ERROR_MESSAGE_BASE = "There is one VacationResponse object per account, with id set to \\\"singleton\\\" and not to ";
    public static final String INVALID_ARGUMENTS1 = "invalidArguments";
    public static final String INVALID_ARGUMENT_DESCRIPTION = "update field should just contain one entry with key \"singleton\"";

    private final VacationService vacationService;
    private final MetricFactory metricFactory;

    @Inject
    public SetVacationResponseMethod(VacationService vacationService, MetricFactory metricFactory) {
        this.vacationService = vacationService;
        this.metricFactory = metricFactory;
    }

    @Override
    public Request.Name requestHandled() {
        return METHOD_NAME;
    }

    @Override
    public Class<? extends JmapRequest> requestType() {
        return SetVacationRequest.class;
    }

    @Override
    public Flux<JmapResponse> process(JmapRequest request, MethodCallId methodCallId, MailboxSession mailboxSession) {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(methodCallId);
        Preconditions.checkNotNull(mailboxSession);
        Preconditions.checkArgument(request instanceof SetVacationRequest);
        SetVacationRequest setVacationRequest = (SetVacationRequest) request;

        return Flux.from(metricFactory.decoratePublisherWithTimerMetric(JMAP_PREFIX + METHOD_NAME.getName(),
            process(methodCallId, mailboxSession, setVacationRequest)
                .contextWrite(jmapAction("SET_VACATION"))
                .contextWrite(context("set-vacation", MDCBuilder.ofValue("update", setVacationRequest.getUpdate().toString())))));
    }

    private Flux<JmapResponse> process(MethodCallId methodCallId, MailboxSession mailboxSession, SetVacationRequest setVacationRequest) {
        if (!setVacationRequest.isValid()) {
            return Flux.just(JmapResponse
                .builder()
                .methodCallId(methodCallId)
                .error(ErrorResponse.builder()
                    .type(INVALID_ARGUMENTS1)
                    .description(INVALID_ARGUMENT_DESCRIPTION)
                    .build())
                .build());
        }

        return process(methodCallId,
            AccountId.fromUsername(mailboxSession.getUser()),
            setVacationRequest.getUpdate().get(Vacation.ID));
    }


    private Flux<JmapResponse> process(MethodCallId methodCallId, AccountId accountId, VacationResponse vacationResponse) {
        if (vacationResponse.isValid()) {
            return vacationService.modifyVacation(toVacationAccountId(accountId), vacationResponse.getPatch())
                .thenMany(Mono.just(JmapResponse.builder()
                    .methodCallId(methodCallId)
                    .responseName(RESPONSE_NAME)
                    .response(SetVacationResponse.builder()
                        .updatedId(Vacation.ID)
                        .build())
                    .build()));
        } else {
            return Flux.just(JmapResponse.builder()
                .methodCallId(methodCallId)
                .responseName(RESPONSE_NAME)
                .response(SetVacationResponse.builder()
                    .notUpdated(Vacation.ID,
                        SetError.builder()
                            .type(SetError.Type.INVALID_ARGUMENTS)
                            .description(ERROR_MESSAGE_BASE + vacationResponse.getId())
                            .build())
                    .build())
                .build());
        }
    }

}
