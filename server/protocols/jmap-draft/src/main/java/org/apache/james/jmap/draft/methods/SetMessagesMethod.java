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

import org.apache.james.jmap.draft.model.MethodCallId;
import org.apache.james.jmap.draft.model.SetMessagesRequest;
import org.apache.james.jmap.draft.model.SetMessagesResponse;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.util.MDCBuilder;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SetMessagesMethod implements Method {
    private static final Method.Request.Name METHOD_NAME = Method.Request.name("setMessages");
    private static final Method.Response.Name RESPONSE_NAME = Method.Response.name("messagesSet");

    private final Set<SetMessagesProcessor> messagesProcessors;

    @Inject
    @VisibleForTesting SetMessagesMethod(Set<SetMessagesProcessor> messagesProcessors) {
        this.messagesProcessors = messagesProcessors;
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
    public Flux<JmapResponse> process(JmapRequest request, MethodCallId methodCallId, MailboxSession mailboxSession) {
        Preconditions.checkArgument(request instanceof SetMessagesRequest);
        SetMessagesRequest setMessagesRequest = (SetMessagesRequest) request;

        return setMessagesResponse(setMessagesRequest, mailboxSession)
            .map(responses -> JmapResponse.builder().methodCallId(methodCallId)
                .response(responses)
                .responseName(RESPONSE_NAME)
                .build())
            .flux()
            .contextWrite(context(ACTION, mdc(setMessagesRequest)));
    }

    private MDCBuilder mdc(SetMessagesRequest setMessagesRequest) {
        return MDCBuilder.create()
            .addToContext(ACTION, "SET_MESSAGES")
            .addToContextIfPresent("accountId", setMessagesRequest.getAccountId())
            .addToContext("create", setMessagesRequest.getCreate().toString())
            .addToContext("destroy", setMessagesRequest.getDestroy().toString())
            .addToContextIfPresent("ifInState", setMessagesRequest.getIfInState());
    }

    private Mono<SetMessagesResponse> setMessagesResponse(SetMessagesRequest request, MailboxSession mailboxSession) {
        return Flux.fromIterable(messagesProcessors)
            .flatMap(processor -> processor.processReactive(request, mailboxSession))
            .reduce(SetMessagesResponse.builder(),
                (builder, resp) -> resp.mergeInto(builder))
            .map(SetMessagesResponse.Builder::build);
    }
}
