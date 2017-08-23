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

import java.io.Closeable;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.jmap.model.ClientId;
import org.apache.james.jmap.model.GetMailboxesRequest;
import org.apache.james.jmap.model.GetMailboxesResponse;
import org.apache.james.jmap.model.MailboxFactory;
import org.apache.james.jmap.model.MailboxProperty;
import org.apache.james.jmap.model.mailbox.Mailbox;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxQuery;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.OptionalConverter;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class GetMailboxesMethod implements Method {

    private static final Method.Request.Name METHOD_NAME = Method.Request.name("getMailboxes");
    private static final Method.Response.Name RESPONSE_NAME = Method.Response.name("mailboxes");

    private final MailboxManager mailboxManager; 
    private final MailboxFactory mailboxFactory;
    private final MetricFactory metricFactory;

    @Inject
    @VisibleForTesting public GetMailboxesMethod(MailboxManager mailboxManager, MailboxFactory mailboxFactory, MetricFactory metricFactory) {
        this.mailboxManager = mailboxManager;
        this.mailboxFactory = mailboxFactory;
        this.metricFactory = metricFactory;
    }

    @Override
    public Method.Request.Name requestHandled() {
        return METHOD_NAME;
    }

    @Override
    public Class<? extends JmapRequest> requestType() {
        return GetMailboxesRequest.class;
    }

    public Stream<JmapResponse> process(JmapRequest request, ClientId clientId, MailboxSession mailboxSession) {
        Preconditions.checkArgument(request instanceof GetMailboxesRequest);
        GetMailboxesRequest mailboxesRequest = (GetMailboxesRequest) request;
        TimeMetric timeMetric = metricFactory.timer(JMAP_PREFIX + METHOD_NAME.getName());
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(MDCBuilder.ACTION, "GET_MAILBOXES")
                     .addContext("accountId", mailboxesRequest.getAccountId())
                     .addContext("mailboxIds", mailboxesRequest.getIds())
                     .addContext("properties", mailboxesRequest.getProperties())
                     .build()) {
            return Stream.of(
                    JmapResponse.builder().clientId(clientId)
                    .response(getMailboxesResponse(mailboxesRequest, mailboxSession))
                    .properties(mailboxesRequest.getProperties().map(this::ensureContainsId))
                    .responseName(RESPONSE_NAME)
                    .build());
        } catch (IOException e) {
            throw Throwables.propagate(e);
        } finally {
            timeMetric.stopAndPublish();
        }
    }

    private Set<MailboxProperty> ensureContainsId(Set<MailboxProperty> input) {
        return Sets.union(input, ImmutableSet.of(MailboxProperty.ID)).immutableCopy();
    }

    private GetMailboxesResponse getMailboxesResponse(GetMailboxesRequest mailboxesRequest, MailboxSession mailboxSession) {
        GetMailboxesResponse.Builder builder = GetMailboxesResponse.builder();
        try {
            Optional<ImmutableList<MailboxId>> mailboxIds = mailboxesRequest.getIds();
            retrieveMailboxes(mailboxIds, mailboxSession)
                .sorted(Comparator.comparing(Mailbox::getSortOrder))
                .forEach(builder::add);
            return builder.build();
        } catch (MailboxException e) {
            throw Throwables.propagate(e);
        }
    }

    private Stream<Mailbox> retrieveMailboxes(Optional<ImmutableList<MailboxId>> mailboxIds, MailboxSession mailboxSession) throws MailboxException {
        if (mailboxIds.isPresent()) {
            return mailboxIds.get()
                .stream()
                .map(mailboxId -> mailboxFactory.builder()
                        .id(mailboxId)
                        .session(mailboxSession)
                        .build())
                .flatMap(OptionalConverter::toStream);
        } else {
            List<MailboxMetaData> userMailboxes = mailboxManager.search(
                MailboxQuery.builder(mailboxSession).privateUserMailboxes().build(),
                mailboxSession);
            return userMailboxes
                .stream()
                .map(MailboxMetaData::getId)
                .map(mailboxId -> mailboxFactory.builder()
                        .id(mailboxId)
                        .session(mailboxSession)
                        .usingPreloadedMailboxesMetadata(userMailboxes)
                        .build())
                .flatMap(OptionalConverter::toStream);
        }
    }
}
