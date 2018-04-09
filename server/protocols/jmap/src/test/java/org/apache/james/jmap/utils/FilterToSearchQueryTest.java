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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;

import javax.mail.Flags.Flag;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.jmap.model.Filter;
import org.apache.james.jmap.model.FilterCondition;
import org.apache.james.jmap.model.FilterOperator;
import org.apache.james.jmap.model.Header;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.AddressType;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class FilterToSearchQueryTest {
    private static final String FORWARDED = "forwarded";

    @Test
    public void filterConditionShouldThrowWhenUnknownFilter() {
        Filter myFilter = (indentation -> null);
        assertThatThrownBy(() -> new FilterToSearchQuery().convert(myFilter))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Unknown filter: " + myFilter.getClass());
    }

    @Test
    public void filterConditionShouldMapEmptyWhenEmptyFilter() {
        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
                .build());

        assertThat(searchQuery).isEqualTo(new SearchQuery());
    }

    @Test
    public void filterConditionShouldMapWhenFrom() {
        String from = "sender@james.org";
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.address(AddressType.From, from));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
                .from(from)
                .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenTo() {
        String to = "recipient@james.org";
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.address(AddressType.To, to));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
                .to(to)
                .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenCc() {
        String cc = "copy@james.org";
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.address(AddressType.Cc, cc));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
                .cc(cc)
                .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenHasAttachment() {
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.hasAttachment());

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
            .hasAttachment(true)
            .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenHasNoAttachment() {
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.hasNoAttachment());

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
            .hasAttachment(false)
            .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenBcc() {
        String bcc = "blindcopy@james.org";
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.address(AddressType.Bcc, bcc));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
                .bcc(bcc)
                .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenSubject() {
        String subject = "subject";
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.headerContains("Subject", subject));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
                .subject(subject)
                .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenBody() {
        String body = "body";
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.bodyContains(body));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
                .body(body)
                .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenAttachments() {
        String attachments = "attachments";
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.attachmentContains(attachments));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
                .attachments(attachments)
                .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenText() {
        String text = "text";
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.or(ImmutableList.of(
                SearchQuery.address(AddressType.From, text),
                SearchQuery.address(AddressType.To, text),
                SearchQuery.address(AddressType.Cc, text),
                SearchQuery.address(AddressType.Bcc, text),
                SearchQuery.headerContains("Subject", text),
                SearchQuery.bodyContains(text),
                SearchQuery.attachmentContains(text))));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
                .text(text)
                .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenAfter() {
        ZonedDateTime after = ZonedDateTime.now();
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.sentDateAfter(Date.from(after.toInstant()), DateResolution.Second));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
                .after(after)
                .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenBefore() {
        ZonedDateTime before = ZonedDateTime.now();
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.sentDateBefore(Date.from(before.toInstant()), DateResolution.Second));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
                .before(before)
                .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenIsAnswered() {
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.flagIsSet(Flag.ANSWERED));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
                .isAnswered(true)
                .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenIsDraft() {
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.flagIsSet(Flag.DRAFT));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
                .isDraft(true)
                .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenIsFlagged() {
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.flagIsSet(Flag.FLAGGED));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
                .isFlagged(true)
                .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenIsUnread() {
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.flagIsUnSet(Flag.SEEN));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
                .isUnread(true)
                .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }


    @Test
    public void filterConditionShouldMapWhenIsNotAnswered() {
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.flagIsUnSet(Flag.ANSWERED));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
            .isAnswered(false)
            .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenIsNotDraft() {
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.flagIsUnSet(Flag.DRAFT));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
            .isDraft(false)
            .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenIsNotFlagged() {
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.flagIsUnSet(Flag.FLAGGED));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
            .isFlagged(false)
            .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenIsRead() {
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.flagIsSet(Flag.SEEN));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
            .isUnread(false)
            .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenMaxSize() {
        int maxSize = 123;
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.sizeLessThan(maxSize));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
                .maxSize(maxSize)
                .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenMinSize() {
        int minSize = 4;
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.sizeGreaterThan(minSize));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
                .minSize(minSize)
                .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenHeaderWithOneElement() {
        String headerName = "name";
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.headerExists(headerName));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
                .header(Header.from(ImmutableList.of(headerName)))
                .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenHeaderWithTwoElements() {
        String headerName = "name";
        String headerValue = "value";
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.headerContains(headerName, headerValue));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
                .header(Header.from(ImmutableList.of(headerName, headerValue)))
                .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapTwoConditions() {
        String from = "sender@james.org";
        String to = "recipient@james.org";
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.and(ImmutableList.of(
                SearchQuery.address(AddressType.From, from),
                SearchQuery.address(AddressType.To, to))));

        Filter filter = FilterOperator.and(
                FilterCondition.builder()
                    .from(from)
                    .build(),
                FilterCondition.builder()
                    .to(to)
                    .build());

        SearchQuery searchQuery = new FilterToSearchQuery().convert(filter);

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenAndOperator() {
        String from = "sender@james.org";
        String to = "recipient@james.org";
        String subject = "subject";
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.and(ImmutableList.of(
                SearchQuery.address(AddressType.From, from),
                SearchQuery.address(AddressType.To, to),
                SearchQuery.headerContains("Subject", subject))));

        Filter complexFilter = FilterOperator.and(
                FilterCondition.builder()
                    .from(from)
                    .to(to)
                    .subject(subject)
                    .build());

        SearchQuery searchQuery = new FilterToSearchQuery().convert(complexFilter);

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenOrOperator() {
        String from = "sender@james.org";
        String to = "recipient@james.org";
        String subject = "subject";
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.or(ImmutableList.of(
                SearchQuery.address(AddressType.From, from),
                SearchQuery.address(AddressType.To, to),
                SearchQuery.headerContains("Subject", subject))));

        Filter complexFilter = FilterOperator.or(
                FilterCondition.builder()
                    .from(from)
                    .to(to)
                    .subject(subject)
                    .build());

        SearchQuery searchQuery = new FilterToSearchQuery().convert(complexFilter);

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenNotOperator() {
        String from = "sender@james.org";
        String to = "recipient@james.org";
        String subject = "subject";
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.not(ImmutableList.of(
                SearchQuery.address(AddressType.From, from),
                SearchQuery.address(AddressType.To, to),
                SearchQuery.headerContains("Subject", subject))));

        Filter complexFilter = FilterOperator.not(
                FilterCondition.builder()
                    .from(from)
                    .to(to)
                    .subject(subject)
                    .build());

        SearchQuery searchQuery = new FilterToSearchQuery().convert(complexFilter);

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenComplexFilterTree() {
        String from = "sender@james.org";
        String to = "recipient@james.org";
        String cc = "copy@james.org";
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.and(ImmutableList.of(
                SearchQuery.address(AddressType.From, from),
                SearchQuery.or(ImmutableList.of(
                        SearchQuery.not(SearchQuery.address(AddressType.To, to)),
                        SearchQuery.address(AddressType.Cc, cc))
                        )
                )));

        Filter complexFilter = FilterOperator.and(
                FilterCondition.builder()
                    .from(from)
                    .build(),
                FilterOperator.or(
                    FilterOperator.not(
                        FilterCondition.builder()
                            .to(to)
                            .build()),
                    FilterCondition.builder()
                        .cc(cc)
                        .build()
                ));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(complexFilter);

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenHasKeyword() {
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.flagIsSet(Flag.FLAGGED));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
                .hasKeyword(Optional.of("$Flagged"))
                .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenHasKeywordWithUserFlag() {
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.flagIsSet(FORWARDED));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
                .hasKeyword(Optional.of(FORWARDED))
                .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenNotKeyword() {
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.flagIsUnSet(Flag.FLAGGED));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
            .notKeyword(Optional.of("$Flagged"))
            .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void filterConditionShouldMapWhenNotKeywordWithUserFlag() {
        SearchQuery expectedSearchQuery = new SearchQuery();
        expectedSearchQuery.andCriteria(SearchQuery.flagIsUnSet(FORWARDED));

        SearchQuery searchQuery = new FilterToSearchQuery().convert(FilterCondition.builder()
                .notKeyword(Optional.of(FORWARDED))
                .build());

        assertThat(searchQuery).isEqualTo(expectedSearchQuery);
    }

    @Test
    public void attachmentFileNameShouldNotBeImplemented() {
        assertThatThrownBy(() -> new FilterToSearchQuery().convert(FilterCondition.builder()
                .attachmentFileName(Optional.of("file.gz"))
                .build()))
            .isInstanceOf(NotImplementedException.class);
    }
}
