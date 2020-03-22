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

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;

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
    public Stream<JmapResponse> processToStream(JmapRequest request, MethodCallId methodCallId, MailboxSession mailboxSession) {
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(methodCallId);
        Preconditions.checkNotNull(mailboxSession);

        Preconditions.checkArgument(request instanceof SetFilterRequest);

        SetFilterRequest setFilterRequest = (SetFilterRequest) request;

        return MDCBuilder.create()
            .addContext(MDCBuilder.ACTION, "SET_FILTER")
            .addContext("update", setFilterRequest.getSingleton())
            .wrapArround(
                () -> metricFactory.runPublishingTimerMetricLogP99(JMAP_PREFIX + METHOD_NAME.getName(),
                    () -> process(methodCallId, mailboxSession, setFilterRequest)))
            .get();
    }

    private Stream<JmapResponse> process(MethodCallId methodCallId, MailboxSession mailboxSession, SetFilterRequest request) {
        try {
            return updateFilter(methodCallId, request, mailboxSession.getUser());
        } catch (MultipleMailboxIdException e) {
            LOGGER.debug("Rule targeting several mailboxes", e);
            return Stream.of(multipleMailboxesError(methodCallId, e));
        }  catch (DuplicatedRuleException e) {
            LOGGER.debug("Duplicated rules", e);
            return Stream.of(duplicatedIdsError(methodCallId, e));
        } catch (Exception e) {
            LOGGER.warn("Failed setting Rules", e);
            return Stream.of(unKnownError(methodCallId));
        }
    }

    private Stream<JmapResponse> updateFilter(MethodCallId methodCallId, SetFilterRequest request, Username username) throws DuplicatedRuleException, MultipleMailboxIdException {
        ImmutableList<Rule> rules = request.getSingleton().stream()
            .map(JmapRuleDTO::toRule)
            .collect(ImmutableList.toImmutableList());

        ensureNoDuplicatedRules(rules);
        ensureNoMultipleMailboxesRules(rules);

        filteringManagement.defineRulesForUser(username, rules);

        return Stream.of(JmapResponse.builder()
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

    private JmapResponse unKnownError(MethodCallId methodCallId) {
        return JmapResponse.builder()
            .methodCallId(methodCallId)
            .responseName(RESPONSE_NAME)
            .response(ErrorResponse.builder()
                .type(SetError.Type.ERROR.asString())
                .description("Failed to retrieve filter")
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
