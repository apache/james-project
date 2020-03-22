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
import org.apache.james.jmap.draft.model.SetMessagesRequest;
import org.apache.james.jmap.draft.model.SetMessagesResponse;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class SetMessagesMethod implements Method {

    private static final Method.Request.Name METHOD_NAME = Method.Request.name("setMessages");
    private static final Method.Response.Name RESPONSE_NAME = Method.Response.name("messagesSet");

    private final Set<SetMessagesProcessor> messagesProcessors;
    private final MetricFactory metricFactory;

    @Inject
    @VisibleForTesting SetMessagesMethod(Set<SetMessagesProcessor> messagesProcessors, MetricFactory metricFactory) {
        this.messagesProcessors = messagesProcessors;
        this.metricFactory = metricFactory;
    }

    @Override
    public Method.Request.Name requestHandled() {
        return METHOD_NAME;
    }

    @Override
    public Class<? extends JmapRequest> requestType() {
        return SetMessagesRequest.class;
    }

    @Override
    public Stream<JmapResponse> processToStream(JmapRequest request, MethodCallId methodCallId, MailboxSession mailboxSession) {
        Preconditions.checkArgument(request instanceof SetMessagesRequest);
        SetMessagesRequest setMessagesRequest = (SetMessagesRequest) request;


        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "SET_MESSAGES")
            .addContext("accountId", setMessagesRequest.getAccountId())
            .addContext("create", setMessagesRequest.getCreate())
            .addContext("destroy", setMessagesRequest.getDestroy())
            .addContext("ifInState", setMessagesRequest.getIfInState())
            .wrapArround(
                () -> metricFactory.runPublishingTimerMetricLogP99(JMAP_PREFIX + METHOD_NAME.getName(),
                    () ->  Stream.of(
                        JmapResponse.builder().methodCallId(methodCallId)
                            .response(setMessagesResponse(setMessagesRequest, mailboxSession))
                            .responseName(RESPONSE_NAME)
                            .build())))
            .get();
    }

    private SetMessagesResponse setMessagesResponse(SetMessagesRequest request, MailboxSession mailboxSession) {
        return messagesProcessors.stream()
                .map(processor -> processor.process(request, mailboxSession))
                .reduce(SetMessagesResponse.builder(),
                        (builder, resp) -> resp.mergeInto(builder),
                        (builder1, builder2) -> builder2.build().mergeInto(builder1)
                )
                .build();
    }
}
