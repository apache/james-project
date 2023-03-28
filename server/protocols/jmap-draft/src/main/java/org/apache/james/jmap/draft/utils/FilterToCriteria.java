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

package org.apache.james.jmap.draft.utils;

import java.util.Date;
import java.util.Optional;
import java.util.stream.Stream;

import javax.mail.Flags.Flag;

import org.apache.james.jmap.draft.model.Filter;
import org.apache.james.jmap.draft.model.FilterCondition;
import org.apache.james.jmap.draft.model.FilterOperator;
import org.apache.james.jmap.draft.model.Keyword;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.AddressType;
import org.apache.james.mailbox.model.SearchQuery.Criterion;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;

import com.google.common.collect.ImmutableList;

public class FilterToCriteria {

    public Stream<Criterion> convert(Filter filter) {
        if (filter instanceof FilterCondition) {
            return convertCondition((FilterCondition) filter);
        }
        if (filter instanceof FilterOperator) {
            return Stream.of(convertOperator((FilterOperator) filter));
        }
        throw new RuntimeException("Unknown filter: " + filter.getClass());
    }

    private Stream<Criterion> convertCondition(FilterCondition filter) {
        ImmutableList.Builder<Criterion> builder = ImmutableList.builder();
        filter.getText().ifPresent(text -> builder.add(
                SearchQuery.or(ImmutableList.of(
                        SearchQuery.address(AddressType.From, text),
                        SearchQuery.address(AddressType.To, text),
                        SearchQuery.address(AddressType.Cc, text),
                        SearchQuery.address(AddressType.Bcc, text),
                        SearchQuery.subject(text),
                        SearchQuery.attachmentContains(text),
                        SearchQuery.bodyContains(text),
                        SearchQuery.attachmentFileName(text)))
                ));
        filter.getFrom().ifPresent(from -> builder.add(SearchQuery.address(AddressType.From, from)));
        filter.getTo().ifPresent(to -> builder.add(SearchQuery.address(AddressType.To, to)));
        filter.getCc().ifPresent(cc -> builder.add(SearchQuery.address(AddressType.Cc, cc)));
        filter.getBcc().ifPresent(bcc -> builder.add(SearchQuery.address(AddressType.Bcc, bcc)));
        filter.getSubject().ifPresent(subject -> builder.add(SearchQuery.subject(subject)));
        filter.getAttachments().ifPresent(attachments ->  builder.add(SearchQuery.attachmentContains(attachments)));
        filter.getBody().ifPresent(body ->  builder.add(SearchQuery.bodyContains(body)));
        filter.getAfter().ifPresent(after -> builder.add(SearchQuery.sentDateAfter(Date.from(after.toInstant()), DateResolution.Second)));
        filter.getBefore().ifPresent(before -> builder.add(SearchQuery.sentDateBefore(Date.from(before.toInstant()), DateResolution.Second)));
        filter.getHeader().ifPresent(header -> builder.add(SearchQuery.headerContains(header.getName(), header.getValue().orElse(null))));
        filter.getIsAnswered().ifPresent(isAnswered -> builder.add(SearchQuery.flag(Flag.ANSWERED, isAnswered)));
        filter.getIsDraft().ifPresent(isDraft -> builder.add(SearchQuery.flag(Flag.DRAFT, isDraft)));
        filter.getIsFlagged().ifPresent(isFlagged -> builder.add(SearchQuery.flag(Flag.FLAGGED, isFlagged)));
        filter.getIsUnread().ifPresent(isUnread -> builder.add(SearchQuery.flag(Flag.SEEN, !isUnread)));
        filter.getIsForwarded().ifPresent(isForwarded -> builder.add(SearchQuery.flagSet(Keyword.FORWARDED.getFlagName(), isForwarded)));
        filter.getMaxSize().ifPresent(maxSize -> builder.add(SearchQuery.sizeLessThan(maxSize.asLong())));
        filter.getMinSize().ifPresent(minSize -> builder.add(SearchQuery.sizeGreaterThan(minSize.asLong())));
        filter.getHasAttachment().ifPresent(hasAttachment -> builder.add(SearchQuery.hasAttachment(hasAttachment)));
        filter.getHasKeyword().ifPresent(hasKeyword -> keywordQuery(hasKeyword, true).ifPresent(builder::add));
        filter.getNotKeyword().ifPresent(notKeyword -> keywordQuery(notKeyword, false).ifPresent(builder::add));
        filter.getAttachmentFileName().ifPresent(attachmentFileName -> builder.add(SearchQuery.attachmentFileName(attachmentFileName)));

        return builder.build().stream();
    }

    private Optional<Criterion> keywordQuery(String stringKeyword, boolean isSet) {
        Keyword keyword = Keyword.of(stringKeyword);
        if (keyword.isExposedImapKeyword()) {
            return Optional.of(getFlagCriterion(keyword, isSet));
        }

        return Optional.empty();
    }

    private Criterion getFlagCriterion(Keyword keyword, boolean isSet) {
        return keyword.asSystemFlag()
            .map(flag -> SearchQuery.flagSet(flag, isSet))
            .orElseGet(() -> SearchQuery.flagSet(keyword.getFlagName(), isSet));
    }

    private Criterion convertOperator(FilterOperator filter) {
        switch (filter.getOperator()) {
        case AND:
            return SearchQuery.and(convertCriterias(filter));
   
        case OR:
            return SearchQuery.or(convertCriterias(filter));
   
        case NOT:
            return SearchQuery.not(convertCriterias(filter));
        }
        throw new RuntimeException("Unknown operator");
    }

    private ImmutableList<Criterion> convertCriterias(FilterOperator filter) {
        return filter.getConditions().stream()
            .flatMap(this::convert)
            .collect(ImmutableList.toImmutableList());
    }
}
