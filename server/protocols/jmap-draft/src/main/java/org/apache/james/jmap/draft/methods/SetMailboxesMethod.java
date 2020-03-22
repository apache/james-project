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

import java.util.Set;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.jmap.draft.model.MethodCallId;
import org.apache.james.jmap.draft.model.SetMailboxesRequest;
import org.apache.james.jmap.draft.model.SetMailboxesResponse;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

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
    public Stream<JmapResponse> processToStream(JmapRequest request, MethodCallId methodCallId, MailboxSession mailboxSession) {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(methodCallId);
        Preconditions.checkNotNull(mailboxSession);
        Preconditions.checkArgument(request instanceof SetMailboxesRequest);

        SetMailboxesRequest setMailboxesRequest = (SetMailboxesRequest) request;


        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "SET_MAILBOXES")
            .addContext("create", setMailboxesRequest.getCreate())
            .addContext("update", setMailboxesRequest.getUpdate())
            .addContext("destroy", setMailboxesRequest.getDestroy())
            .wrapArround(
                () -> metricFactory.runPublishingTimerMetricLogP99(JMAP_PREFIX + METHOD_NAME.getName(),
                    () -> Stream.of(
                        JmapResponse.builder().methodCallId(methodCallId)
                            .response(setMailboxesResponse(setMailboxesRequest, mailboxSession))
                            .responseName(RESPONSE_NAME)
                            .build())))
            .get();
    }

    private SetMailboxesResponse setMailboxesResponse(SetMailboxesRequest request, MailboxSession mailboxSession) {
        return processors.stream()
                .map(processor -> processor.process(request, mailboxSession))
                .reduce(SetMailboxesResponse.builder(),
                        (builder, resp) -> resp.mergeInto(builder),
                        (builder1, builder2) -> builder2.build().mergeInto(builder1)
                )
                .build();
    }
}