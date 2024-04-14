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

import static org.apache.james.util.MDCBuilder.ACTION;
import static org.apache.james.util.ReactorUtils.context;

import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.jmap.draft.model.SetMailboxesRequest;
import org.apache.james.jmap.draft.model.SetMailboxesResponse;
import org.apache.james.jmap.methods.JmapRequest;
import org.apache.james.jmap.methods.JmapResponse;
import org.apache.james.jmap.methods.Method;
import org.apache.james.jmap.model.MethodCallId;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SetMailboxesMethod implements Method {

    private static final Request.Name METHOD_NAME = Request.name("setMailboxes");
    @VisibleForTesting static final Response.Name RESPONSE_NAME = Response.name("mailboxesSet");

    private final Set<SetMailboxesProcessor> processors;
    private final MetricFactory metricFactory;

    @Inject
    public SetMailboxesMethod(Set<SetMailboxesProcessor> processors, MetricFactory metricFactory) {
        this.processors = processors;
        this.metricFactory = metricFactory;
    }

    @Override
    public Request.Name requestHandled() {
        return METHOD_NAME;
    }

    @Override
    public Class<? extends JmapRequest> requestType() {
        return SetMailboxesRequest.class;
    }

    @Override
    public Flux<JmapResponse> process(JmapRequest request, MethodCallId methodCallId, MailboxSession mailboxSession) {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(methodCallId);
        Preconditions.checkNotNull(mailboxSession);
        Preconditions.checkArgument(request instanceof SetMailboxesRequest);

        SetMailboxesRequest setMailboxesRequest = (SetMailboxesRequest) request;

        return Flux.from(metricFactory.decoratePublisherWithTimerMetric(JMAP_PREFIX + METHOD_NAME.getName(),
            setMailboxesResponse(setMailboxesRequest, mailboxSession)
                .map(response -> JmapResponse.builder().methodCallId(methodCallId)
                    .response(response)
                    .responseName(RESPONSE_NAME)
                    .build())))
            .contextWrite(context(ACTION, mdc(setMailboxesRequest)));
    }

    private MDCBuilder mdc(SetMailboxesRequest setMailboxesRequest) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, "SET_MAILBOXES")
            .addToContext("create", setMailboxesRequest.getCreate().toString())
            .addToContext("update", setMailboxesRequest.getUpdate().toString())
            .addToContext("destroy", setMailboxesRequest.getDestroy().toString());
    }

    private Mono<SetMailboxesResponse> setMailboxesResponse(SetMailboxesRequest request, MailboxSession mailboxSession) {
        return Flux.fromIterable(processors)
            .flatMap(processor -> processor.processReactive(request, mailboxSession))
            .reduce(SetMailboxesResponse.builder(),
                (builder, resp) -> resp.mergeInto(builder))
            .map(SetMailboxesResponse.Builder::build);
    }
}