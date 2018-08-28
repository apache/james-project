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

import java.util.List;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.core.User;
import org.apache.james.jmap.api.filtering.FilteringManagement;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.model.ClientId;
import org.apache.james.jmap.model.GetFilterRequest;
import org.apache.james.jmap.model.GetFilterResponse;
import org.apache.james.jmap.model.SetError;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class GetFilterMethod implements Method {
    private static final Logger LOGGER = LoggerFactory.getLogger(GetFilterMethod.class);

    private static final Method.Request.Name METHOD_NAME = Method.Request.name("getFilter");
    private static final Method.Response.Name RESPONSE_NAME = Method.Response.name("filter");

    private final MetricFactory metricFactory;
    private final FilteringManagement filteringManagement;

    @Inject
    private GetFilterMethod(MetricFactory metricFactory, FilteringManagement filteringManagement) {
        this.metricFactory = metricFactory;
        this.filteringManagement = filteringManagement;
    }

    @Override
    public Request.Name requestHandled() {
        return METHOD_NAME;
    }

    @Override
    public Class<? extends JmapRequest> requestType() {
        return GetFilterRequest.class;
    }

    @Override
    public Stream<JmapResponse> process(JmapRequest request, ClientId clientId, MailboxSession mailboxSession) {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(clientId);
        Preconditions.checkNotNull(mailboxSession);
        Preconditions.checkArgument(request instanceof GetFilterRequest);

        GetFilterRequest filterRequest = (GetFilterRequest) request;

        return metricFactory.withMetric(JMAP_PREFIX + METHOD_NAME.getName(),
            MDCBuilder.create()
                .addContext(MDCBuilder.ACTION, "GET_FILTER")
                .wrapArround(() -> process(clientId, mailboxSession, filterRequest)));
    }

    private Stream<JmapResponse> process(ClientId clientId, MailboxSession mailboxSession, GetFilterRequest request) {
        try {
            User user = User.fromUsername(mailboxSession.getUser().getUserName());

            return retrieveFilter(clientId, user);
        } catch (Exception e) {
            LOGGER.warn("Failed to retrieve filter");

            return Stream.of(unKnownError(clientId));
        }
    }

    private Stream<JmapResponse> retrieveFilter(ClientId clientId, User user) {
        List<Rule> rules = filteringManagement.listRulesForUser(user);

        GetFilterResponse getFilterResponse = GetFilterResponse.builder()
            .rules(rules)
            .build();

        return Stream.of(JmapResponse.builder()
            .clientId(clientId)
            .response(getFilterResponse)
            .responseName(RESPONSE_NAME)
            .build());
    }

    private JmapResponse unKnownError(ClientId clientId) {
        return JmapResponse.builder()
            .clientId(clientId)
            .responseName(RESPONSE_NAME)
            .response(ErrorResponse.builder()
                .type(SetError.Type.ERROR.asString())
                .description("Failed to retrieve filter")
                .build())
            .build();
    }
}
