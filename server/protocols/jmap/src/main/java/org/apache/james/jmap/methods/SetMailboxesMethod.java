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

import java.util.Set;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.jmap.model.ClientId;
import org.apache.james.jmap.model.SetMailboxesRequest;
import org.apache.james.jmap.model.SetMailboxesResponse;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.store.mail.model.MailboxId;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class SetMailboxesMethod<Id extends MailboxId> implements Method {

    private static final Request.Name METHOD_NAME = Request.name("setMailboxes");
    @VisibleForTesting static final Response.Name RESPONSE_NAME = Response.name("mailboxes");

    private final Set<SetMailboxesProcessor<Id>> processors;

    @Inject
    public SetMailboxesMethod(Set<SetMailboxesProcessor<Id>> processors) {
        this.processors = processors;
    }

    @Override
    public Request.Name requestHandled() {
        return METHOD_NAME;
    }

    @Override
    public Class<? extends JmapRequest> requestType() {
        return org.apache.james.jmap.model.SetMailboxesRequest.class;
    }

    @Override
    public Stream<JmapResponse> process(JmapRequest request, ClientId clientId, MailboxSession mailboxSession) {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(clientId);
        Preconditions.checkNotNull(mailboxSession);
        Preconditions.checkArgument(request instanceof SetMailboxesRequest);
        SetMailboxesRequest setMailboxesRequest = (SetMailboxesRequest) request;
        return processors.stream()
            .map(processor -> processor.process(setMailboxesRequest))
            .map(response -> toJmapResponse(clientId, response)
            );
    }

    private JmapResponse toJmapResponse(ClientId clientId, SetMailboxesResponse response) {
        return JmapResponse.builder()
                .clientId(clientId)
                .responseName(RESPONSE_NAME)
                .response(response)
                .build();
    }
}