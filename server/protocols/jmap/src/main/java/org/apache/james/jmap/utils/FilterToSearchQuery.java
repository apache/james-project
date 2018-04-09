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

package org.apache.james.jmap.utils;

import java.util.Date;
import java.util.Optional;

import javax.mail.Flags.Flag;

import org.apache.james.jmap.model.Filter;
import org.apache.james.jmap.model.FilterCondition;
import org.apache.james.jmap.model.FilterOperator;
import org.apache.james.jmap.model.Keyword;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.AddressType;
import org.apache.james.mailbox.model.SearchQuery.Criterion;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public class FilterToSearchQuery {

    public SearchQuery convert(Filter filter) {
        if (filter instanceof FilterCondition) {
            return convertCondition((FilterCondition) filter);
        }
        if (filter instanceof FilterOperator) {
            SearchQuery searchQuery = new SearchQuery();
            searchQuery.andCriteria(convertOperator((FilterOperator) filter));
            return searchQuery;
        }
        throw new RuntimeException("Unknown filter: " + filter.getClass());
    }

    private SearchQuery convertCondition(FilterCondition filter) {
        SearchQuery searchQuery = new SearchQuery();
        filter.getText().ifPresent(text -> searchQuery.andCriteria(
                SearchQuery.or(ImmutableList.of(
                        SearchQuery.address(AddressType.From, text),
                        SearchQuery.address(AddressType.To, text),
                        SearchQuery.address(AddressType.Cc, text),
                        SearchQuery.address(AddressType.Bcc, text),
                        SearchQuery.headerContains("Subject", text),
                        SearchQuery.attachmentContains(text),
                        SearchQuery.bodyContains(text),
                        SearchQuery.attachmentFileName(text)))
                ));
        filter.getFrom().ifPresent(from -> searchQuery.andCriteria(SearchQuery.address(AddressType.From, from)));
        filter.getTo().ifPresent(to -> searchQuery.andCriteria(SearchQuery.address(AddressType.To, to)));
        filter.getCc().ifPresent(cc -> searchQuery.andCriteria(SearchQuery.address(AddressType.Cc, cc)));
        filter.getBcc().ifPresent(bcc -> searchQuery.andCriteria(SearchQuery.address(AddressType.Bcc, bcc)));
        filter.getSubject().ifPresent(subject -> searchQuery.andCriteria(SearchQuery.headerContains("Subject", subject)));
        filter.getAttachments().ifPresent(attachments ->  searchQuery.andCriteria(SearchQuery.attachmentContains(attachments)));
        filter.getBody().ifPresent(body ->  searchQuery.andCriteria(SearchQuery.bodyContains(body)));
        filter.getAfter().ifPresent(after -> searchQuery.andCriteria(SearchQuery.sentDateAfter(Date.from(after.toInstant()), DateResolution.Second)));
        filter.getBefore().ifPresent(before -> searchQuery.andCriteria(SearchQuery.sentDateBefore(Date.from(before.toInstant()), DateResolution.Second)));
        filter.getHeader().ifPresent(header -> searchQuery.andCriteria(SearchQuery.headerContains(header.getName(), header.getValue().orElse(null))));
        filter.getIsAnswered().ifPresent(isAnswered -> searchQuery.andCriteria(SearchQuery.flag(Flag.ANSWERED, isAnswered)));
        filter.getIsDraft().ifPresent(isDraft -> searchQuery.andCriteria(SearchQuery.flag(Flag.DRAFT, isDraft)));
        filter.getIsFlagged().ifPresent(isFlagged -> searchQuery.andCriteria(SearchQuery.flag(Flag.FLAGGED, isFlagged)));
        filter.getIsUnread().ifPresent(isUnread -> searchQuery.andCriteria(SearchQuery.flag(Flag.SEEN, !isUnread)));
        filter.getIsForwarded().ifPresent(isForwarded -> searchQuery.andCriteria(SearchQuery.flagSet(Keyword.FORWARDED.getFlagName(), isForwarded)));
        filter.getMaxSize().ifPresent(maxSize -> searchQuery.andCriteria(SearchQuery.sizeLessThan(maxSize.asLong())));
        filter.getMinSize().ifPresent(minSize -> searchQuery.andCriteria(SearchQuery.sizeGreaterThan(minSize.asLong())));
        filter.getHasAttachment().ifPresent(hasAttachment -> searchQuery.andCriteria(SearchQuery.hasAttachment(hasAttachment)));
        filter.getHasKeyword().ifPresent(hasKeyword -> keywordQuery(hasKeyword, true).ifPresent(searchQuery::andCriteria));
        filter.getNotKeyword().ifPresent(notKeyword -> keywordQuery(notKeyword, false).ifPresent(searchQuery::andCriteria));
        filter.getAttachmentFileName().ifPresent(attachmentFileName -> searchQuery.andCriteria(SearchQuery.attachmentFileName(attachmentFileName)));

        return searchQuery;
    }

    private Optional<Criterion> keywordQuery(String stringKeyword, boolean isSet) {
        Keyword keyword = new Keyword(stringKeyword);
        if (keyword.isExposedImapKeyword()) {
            return Optional.of(getFlagCriterion(keyword, isSet));
        }

        return Optional.empty();
    }

    private Criterion getFlagCriterion(Keyword keyword, boolean isSet) {
        return keyword.asSystemFlag()
            .map(flag -> SearchQuery.flagSet(flag, isSet))
            .orElse(SearchQuery.flagSet(keyword.getFlagName(), isSet));
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
            .map(this::convert)
            .flatMap(sq -> sq.getCriterias().stream())
            .collect(Guavate.toImmutableList());
    }
}
