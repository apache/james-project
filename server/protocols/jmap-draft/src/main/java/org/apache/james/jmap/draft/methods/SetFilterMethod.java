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

import static org.apache.james.jmap.http.LoggingHelper.jmapAction;
import static org.apache.james.util.ReactorUtils.context;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.filtering.FilteringManagement;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.draft.model.JmapRuleDTO;
import org.apache.james.jmap.draft.model.MethodCallId;
import org.apache.james.jmap.draft.model.SetError;
import org.apache.james.jmap.draft.model.SetFilterRequest;
import org.apache.james.jmap.draft.model.SetFilterResponse;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SetFilterMethod implements Method {

    public static class DuplicatedRuleException extends Exception {
        private final ImmutableList<Rule.Id> duplicatedIds;

        public DuplicatedRuleException(ImmutableList<Rule.Id> duplicatedIds) {
            super("The following rules were duplicated:" + format(duplicatedIds));
            this.duplicatedIds = duplicatedIds;
        }
    }

    public static class MultipleMailboxIdException extends Exception {
        private final ImmutableList<Rule.Id> idsWithMultipleMailboxes;

        public MultipleMailboxIdException(ImmutableList<Rule.Id> idsWithMultipleMailboxes) {
            super("The following rules were targeting several mailboxes:" + format(idsWithMultipleMailboxes));
            this.idsWithMultipleMailboxes = idsWithMultipleMailboxes;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SetFilterMethod.class);

    private static final Request.Name METHOD_NAME = Request.name("setFilter");
    private static final Response.Name RESPONSE_NAME = Response.name("filterSet");

    private static String format(ImmutableList<Rule.Id> ids) {
        return "[" + ids.stream()
                .map(Rule.Id::asString)
                .map(SetFilterMethod::quote)
                .collect(Collectors.joining(","))
                + "]";
    }

    private static String quote(String s) {
        return "'" + s + "'";
    }

    private final MetricFactory metricFactory;
    private final FilteringManagement filteringManagement;

    @Inject
    public SetFilterMethod(MetricFactory metricFactory, FilteringManagement filteringManagement) {
        this.filteringManagement = filteringManagement;
        this.metricFactory = metricFactory;
    }

    @Override
    public Request.Name requestHandled() {
        return METHOD_NAME;
    }

    @Override
    public Class<? extends JmapRequest> requestType() {
        return SetFilterRequest.class;
    }

    @Override
    public Flux<JmapResponse> process(JmapRequest request, MethodCallId methodCallId, MailboxSession mailboxSession) {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(methodCallId);
        Preconditions.checkNotNull(mailboxSession);

        Preconditions.checkArgument(request instanceof SetFilterRequest);

        SetFilterRequest setFilterRequest = (SetFilterRequest) request;

        return Flux.from(metricFactory.decoratePublisherWithTimerMetric(JMAP_PREFIX + METHOD_NAME.getName(),
            process(methodCallId, mailboxSession, setFilterRequest)
                .contextWrite(jmapAction("SET_FILTER"))
                .contextWrite(context("SET_FILTER", MDCBuilder.ofValue("update", setFilterRequest.getSingleton().toString())))));
    }

    private Mono<JmapResponse> process(MethodCallId methodCallId, MailboxSession mailboxSession, SetFilterRequest request) {
        try {
            return updateFilter(methodCallId, request, mailboxSession.getUser())
                .doOnEach(ReactorUtils.logOnError(e -> LOGGER.warn("Failed setting Rules", e)))
                .onErrorResume(e -> Mono.just(unknownError(methodCallId)));
        } catch (MultipleMailboxIdException e) {
            LOGGER.debug("Rule targeting several mailboxes", e);
            return Mono.just(multipleMailboxesError(methodCallId, e));
        }  catch (DuplicatedRuleException e) {
            LOGGER.debug("Duplicated rules", e);
            return Mono.just(duplicatedIdsError(methodCallId, e));
        }  catch (IllegalArgumentException e) {
            LOGGER.warn("IllegalArgumentException of setting Rules", e);
            return Mono.just(invalidArgumentsError(methodCallId, e.getMessage()));
        } catch (IllegalStateException e) {
            LOGGER.warn("IllegalStateException of setting Rules", e);
            return Mono.just(invalidArgumentsError(methodCallId, e.getMessage()));
        } catch (Exception e) {
            LOGGER.warn("Failed setting Rules", e);
            return Mono.just(unknownError(methodCallId));
        }
    }

    private Mono<JmapResponse> updateFilter(MethodCallId methodCallId, SetFilterRequest request, Username username) throws DuplicatedRuleException, MultipleMailboxIdException {
        ImmutableList<Rule> rules = request.getSingleton().stream()
            .map(JmapRuleDTO::toRule)
            .collect(ImmutableList.toImmutableList());

        ensureNoDuplicatedRules(rules);
        ensureNoMultipleMailboxesRules(rules);

        return Mono.from(filteringManagement.defineRulesForUser(username, rules, Optional.empty()))
            .thenReturn(JmapResponse.builder()
                .methodCallId(methodCallId)
                .responseName(RESPONSE_NAME)
                .response(SetFilterResponse.updated())
                .build());
    }

    private void ensureNoMultipleMailboxesRules(ImmutableList<Rule> rules) throws MultipleMailboxIdException {
        ImmutableList<Rule.Id> idWithMultipleMailboxes = rules.stream()
            .filter(rule -> rule.getAction().getAppendInMailboxes().getMailboxIds().size() > 1)
            .map(Rule::getId)
            .collect(ImmutableList.toImmutableList());

        if (!idWithMultipleMailboxes.isEmpty()) {
            throw new MultipleMailboxIdException(idWithMultipleMailboxes);
        }
    }

    private void ensureNoDuplicatedRules(List<Rule> rules) throws DuplicatedRuleException {
        ImmutableList<Rule.Id> duplicatedIds = rules.stream()
            .collect(ImmutableListMultimap.toImmutableListMultimap(
                Rule::getId,
                Function.identity()))
            .asMap()
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue().size() > 1)
            .map(Map.Entry::getKey)
            .collect(ImmutableList.toImmutableList());

        if (!duplicatedIds.isEmpty()) {
            throw new DuplicatedRuleException(duplicatedIds);
        }
    }

    private JmapResponse unknownError(MethodCallId methodCallId) {
        return unknownError(methodCallId, "Failed to retrieve filter");
    }

    private JmapResponse invalidArgumentsError(MethodCallId methodCallId, String errorMessage) {
        return JmapResponse.builder()
            .methodCallId(methodCallId)
            .responseName(RESPONSE_NAME)
            .response(ErrorResponse.builder()
                .type(SetError.Type.INVALID_ARGUMENTS.asString())
                .description(errorMessage)
                .build())
            .build();
    }

    private JmapResponse unknownError(MethodCallId methodCallId, String errorMessage) {
        return JmapResponse.builder()
            .methodCallId(methodCallId)
            .responseName(RESPONSE_NAME)
            .response(ErrorResponse.builder()
                .type(SetError.Type.ERROR.asString())
                .description(errorMessage)
                .build())
            .build();
    }

    private JmapResponse duplicatedIdsError(MethodCallId methodCallId, DuplicatedRuleException e) {
        return JmapResponse.builder()
            .methodCallId(methodCallId)
            .responseName(RESPONSE_NAME)
            .response(SetFilterResponse.notUpdated(SetError.builder()
                .type(SetError.Type.INVALID_ARGUMENTS)
                .description("The following rules were duplicated: " + format(e.duplicatedIds))
                .build()))
            .build();
    }

    private JmapResponse multipleMailboxesError(MethodCallId methodCallId, MultipleMailboxIdException e) {
        return JmapResponse.builder()
            .methodCallId(methodCallId)
            .responseName(RESPONSE_NAME)
            .response(SetFilterResponse.notUpdated(SetError.builder()
                .type(SetError.Type.INVALID_ARGUMENTS)
                .description("The following rules targeted several mailboxes, which is not supported: " + format(e.idsWithMultipleMailboxes))
                .build()))
            .build();
    }
}
