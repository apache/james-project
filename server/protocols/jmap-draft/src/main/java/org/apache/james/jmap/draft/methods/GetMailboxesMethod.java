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

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.jmap.draft.model.GetMailboxesRequest;
import org.apache.james.jmap.draft.model.GetMailboxesResponse;
import org.apache.james.jmap.draft.model.MailboxFactory;
import org.apache.james.jmap.draft.model.MailboxProperty;
import org.apache.james.jmap.draft.model.MethodCallId;
import org.apache.james.jmap.draft.model.mailbox.Mailbox;
import org.apache.james.jmap.draft.utils.quotas.QuotaLoader;
import org.apache.james.jmap.draft.utils.quotas.QuotaLoaderWithDefaultPreloaded;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.OptionalUtils;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class GetMailboxesMethod implements Method {

    private static final Method.Request.Name METHOD_NAME = Method.Request.name("getMailboxes");
    private static final Method.Response.Name RESPONSE_NAME = Method.Response.name("mailboxes");
    private static final Optional<List<MailboxMetaData>> NO_PRELOADED_METADATA = Optional.empty();

    private final MailboxManager mailboxManager;
    private final MailboxFactory mailboxFactory;
    private final MetricFactory metricFactory;
    private final QuotaRootResolver quotaRootResolver;
    private final QuotaManager quotaManager;

    @Inject
    @VisibleForTesting
    public GetMailboxesMethod(MailboxManager mailboxManager, QuotaRootResolver quotaRootResolver, QuotaManager quotaManager, MailboxFactory mailboxFactory, MetricFactory metricFactory) {
        this.mailboxManager = mailboxManager;
        this.mailboxFactory = mailboxFactory;
        this.metricFactory = metricFactory;
        this.quotaRootResolver = quotaRootResolver;
        this.quotaManager = quotaManager;
    }

    @Override
    public Method.Request.Name requestHandled() {
        return METHOD_NAME;
    }

    @Override
    public Class<? extends JmapRequest> requestType() {
        return GetMailboxesRequest.class;
    }

    @Override
    public Stream<JmapResponse> process(JmapRequest request, MethodCallId methodCallId, MailboxSession mailboxSession) {
        Preconditions.checkArgument(request instanceof GetMailboxesRequest);
        GetMailboxesRequest mailboxesRequest = (GetMailboxesRequest) request;

        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "GET_MAILBOXES")
            .addContext("accountId", mailboxesRequest.getAccountId())
            .addContext("mailboxIds", mailboxesRequest.getIds())
            .addContext("properties", mailboxesRequest.getProperties())
            .wrapArround(
                () -> metricFactory.runPublishingTimerMetricLogP99(JMAP_PREFIX + METHOD_NAME.getName(),
                    () -> process(methodCallId, mailboxSession, mailboxesRequest)))
            .get();
    }

    private Stream<JmapResponse> process(MethodCallId methodCallId, MailboxSession mailboxSession, GetMailboxesRequest mailboxesRequest) {
        return Stream.of(
            JmapResponse.builder().methodCallId(methodCallId)
                .response(getMailboxesResponse(mailboxesRequest, mailboxSession))
                .properties(mailboxesRequest.getProperties().map(this::ensureContainsId))
                .responseName(RESPONSE_NAME)
                .build());
    }

    private Set<MailboxProperty> ensureContainsId(Set<MailboxProperty> input) {
        return Sets.union(input, ImmutableSet.of(MailboxProperty.ID)).immutableCopy();
    }

    private GetMailboxesResponse getMailboxesResponse(GetMailboxesRequest mailboxesRequest, MailboxSession mailboxSession) {
        GetMailboxesResponse.Builder builder = GetMailboxesResponse.builder();
        try {
            Optional<ImmutableList<MailboxId>> mailboxIds = mailboxesRequest.getIds();
            List<Mailbox> mailboxes = retrieveMailboxes(mailboxIds, mailboxSession)
                .sorted(Comparator.comparing(Mailbox::getSortOrder))
                .collect(Guavate.toImmutableList());
            return builder.addAll(mailboxes).build();
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }

    private Stream<Mailbox> retrieveMailboxes(Optional<ImmutableList<MailboxId>> mailboxIds, MailboxSession mailboxSession) throws MailboxException {
        return mailboxIds
            .map(ids -> retrieveSpecificMailboxes(mailboxSession, ids))
            .orElseGet(Throwing.supplier(() -> retrieveAllMailboxes(mailboxSession)).sneakyThrow());
    }


    private Stream<Mailbox> retrieveSpecificMailboxes(MailboxSession mailboxSession, ImmutableList<MailboxId> mailboxIds) {
        return mailboxIds
            .stream()
            .map(mailboxId -> mailboxFactory.builder()
                .id(mailboxId)
                .session(mailboxSession)
                .usingPreloadedMailboxesMetadata(NO_PRELOADED_METADATA)
                .build()
            )
            .flatMap(OptionalUtils::toStream);
    }

    private Stream<Mailbox> retrieveAllMailboxes(MailboxSession mailboxSession) throws MailboxException {
        List<MailboxMetaData> userMailboxes = getAllMailboxesMetaData(mailboxSession);
        QuotaLoader quotaLoader = new QuotaLoaderWithDefaultPreloaded(quotaRootResolver, quotaManager, mailboxSession);

        return userMailboxes
            .stream()
            .map(mailboxMetaData -> mailboxFactory.builder()
                .mailboxMetadata(mailboxMetaData)
                .session(mailboxSession)
                .usingPreloadedMailboxesMetadata(Optional.of(userMailboxes))
                .quotaLoader(quotaLoader)
                .build())
            .flatMap(OptionalUtils::toStream);
    }

    private List<MailboxMetaData> getAllMailboxesMetaData(MailboxSession mailboxSession) throws MailboxException {
        return mailboxManager.search(
            MailboxQuery.builder()
                .matchesAllMailboxNames()
                .build(),
            mailboxSession);
    }

}
