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

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;
import static org.apache.james.util.ReactorUtils.context;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.jmap.draft.model.GetMailboxesRequest;
import org.apache.james.jmap.draft.model.GetMailboxesResponse;
import org.apache.james.jmap.draft.model.MailboxFactory;
import org.apache.james.jmap.draft.model.MailboxProperty;
import org.apache.james.jmap.draft.model.MethodCallId;
import org.apache.james.jmap.draft.model.mailbox.Mailbox;
import org.apache.james.jmap.draft.utils.quotas.QuotaLoaderWithDefaultPreloaded;
import org.apache.james.jmap.http.DefaultMailboxesProvisioner;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class GetMailboxesMethod implements Method {

    private static final Method.Request.Name METHOD_NAME = Method.Request.name("getMailboxes");
    private static final Method.Response.Name RESPONSE_NAME = Method.Response.name("mailboxes");
    private static final Optional<Map<MailboxPath, MailboxMetaData>> NO_PRELOADED_METADATA = Optional.empty();
    private static final String ACTION = "GET_MAILBOXES";

    private final MailboxManager mailboxManager;
    private final MailboxFactory mailboxFactory;
    private final MetricFactory metricFactory;
    private final QuotaRootResolver quotaRootResolver;
    private final QuotaManager quotaManager;
    private final DefaultMailboxesProvisioner provisioner;

    @Inject
    @VisibleForTesting
    public GetMailboxesMethod(MailboxManager mailboxManager, QuotaRootResolver quotaRootResolver, QuotaManager quotaManager, MailboxFactory mailboxFactory, MetricFactory metricFactory, DefaultMailboxesProvisioner provisioner) {
        this.mailboxManager = mailboxManager;
        this.mailboxFactory = mailboxFactory;
        this.metricFactory = metricFactory;
        this.quotaRootResolver = quotaRootResolver;
        this.quotaManager = quotaManager;
        this.provisioner = provisioner;
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
    public Flux<JmapResponse> process(JmapRequest request, MethodCallId methodCallId, MailboxSession mailboxSession) {
        Preconditions.checkArgument(request instanceof GetMailboxesRequest);
        GetMailboxesRequest mailboxesRequest = (GetMailboxesRequest) request;

        return Flux.from(metricFactory.decoratePublisherWithTimerMetric(JMAP_PREFIX + METHOD_NAME.getName(),
            process(methodCallId, mailboxSession, mailboxesRequest)
                .contextWrite(context(ACTION, mdc(mailboxesRequest)))));
    }

    private MDCBuilder mdc(GetMailboxesRequest mailboxesRequest) {
        return MDCBuilder.create()
            .addToContext(MDCBuilder.ACTION, ACTION)
            .addToContextIfPresent("accountId", mailboxesRequest.getAccountId())
            .addToContext("mailboxIds", mailboxesRequest.getIds().toString())
            .addToContext("properties", mailboxesRequest.getProperties().toString());
    }

    private Flux<JmapResponse> process(MethodCallId methodCallId, MailboxSession mailboxSession, GetMailboxesRequest mailboxesRequest) {
        return provisioner.createMailboxesIfNeeded(mailboxSession)
            .thenMany(Flux.from(getMailboxesResponse(mailboxesRequest, mailboxSession)
                .map(response -> JmapResponse.builder().methodCallId(methodCallId)
                    .response(response)
                    .properties(mailboxesRequest.getProperties().map(this::ensureContainsId))
                    .responseName(RESPONSE_NAME)
                    .build())));
    }

    private Set<MailboxProperty> ensureContainsId(Set<MailboxProperty> input) {
        return Sets.union(input, ImmutableSet.of(MailboxProperty.ID)).immutableCopy();
    }

    private Mono<GetMailboxesResponse> getMailboxesResponse(GetMailboxesRequest mailboxesRequest, MailboxSession mailboxSession) {
        Optional<ImmutableList<MailboxId>> mailboxIds = mailboxesRequest.getIds();
        return retrieveMailboxes(mailboxIds, mailboxSession)
            .sort(Comparator.comparing(Mailbox::getSortOrder))
            .reduce(GetMailboxesResponse.builder(), GetMailboxesResponse.Builder::add)
            .map(GetMailboxesResponse.Builder::build);
    }

    private Flux<Mailbox> retrieveMailboxes(Optional<ImmutableList<MailboxId>> mailboxIds, MailboxSession mailboxSession) {
        return mailboxIds
            .map(ids -> retrieveSpecificMailboxes(mailboxSession, ids))
            .orElseGet(Throwing.supplier(() -> retrieveAllMailboxes(mailboxSession)).sneakyThrow());
    }


    private Flux<Mailbox> retrieveSpecificMailboxes(MailboxSession mailboxSession, ImmutableList<MailboxId> mailboxIds) {
        return Flux.fromIterable(mailboxIds)
            .flatMap(mailboxId -> mailboxFactory.builder()
                    .id(mailboxId)
                    .session(mailboxSession)
                    .usingPreloadedMailboxesMetadata(NO_PRELOADED_METADATA)
                    .build(), DEFAULT_CONCURRENCY);
    }

    private Flux<Mailbox> retrieveAllMailboxes(MailboxSession mailboxSession) {
        Mono<Map<MailboxPath, MailboxMetaData>> userMailboxesMono = getAllMailboxesMetaData(mailboxSession).collectMap(m -> m.getPath());
        Mono<QuotaLoaderWithDefaultPreloaded> quotaLoaderMono = QuotaLoaderWithDefaultPreloaded.preLoad(quotaRootResolver, quotaManager, mailboxSession);

        return userMailboxesMono.zipWith(quotaLoaderMono)
            .publishOn(Schedulers.parallel())
            .flatMapMany(
                tuple -> Flux.fromIterable(tuple.getT1().values())
                    .flatMap(mailboxMetaData -> mailboxFactory.builder()
                        .mailboxMetadata(mailboxMetaData)
                        .session(mailboxSession)
                        .usingPreloadedMailboxesMetadata(Optional.of(tuple.getT1()))
                        .quotaLoader(tuple.getT2())
                        .build()));
    }

    private Flux<MailboxMetaData> getAllMailboxesMetaData(MailboxSession mailboxSession) {
        return mailboxManager.search(
            MailboxQuery.builder()
                .matchesAllMailboxNames()
                .build(),
            mailboxSession);
    }

}
