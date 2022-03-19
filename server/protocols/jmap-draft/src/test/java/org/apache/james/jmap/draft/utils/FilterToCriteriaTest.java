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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.mail.Flags.Flag;

import org.apache.james.jmap.draft.model.Filter;
import org.apache.james.jmap.draft.model.FilterCondition;
import org.apache.james.jmap.draft.model.FilterOperator;
import org.apache.james.jmap.draft.model.Header;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.AddressType;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class FilterToCriteriaTest {
    private static final String FORWARDED = "forwarded";

    @Test
    public void filterConditionShouldThrowWhenUnknownFilter() {
        Filter myFilter = (indentation -> null);
        assertThatThrownBy(() -> new FilterToCriteria().convert(myFilter))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Unknown filter: " + myFilter.getClass());
    }

    @Test
    public void filterConditionShouldMapEmptyWhenEmptyFilter() {
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder().build());

        assertThat(criteria).isEmpty();
    }

    @Test
    public void filterConditionShouldMapWhenFrom() {
        String from = "sender@james.org";
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
                .from(from)
                .build());

        assertThat(criteria).containsExactly(SearchQuery.address(AddressType.From, from));
    }

    @Test
    public void filterConditionShouldMapWhenTo() {
        String to = "recipient@james.org";
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
                .to(to)
                .build());

        assertThat(criteria).containsExactly(SearchQuery.address(AddressType.To, to));
    }

    @Test
    public void filterConditionShouldMapWhenCc() {
        String cc = "copy@james.org";
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
                .cc(cc)
                .build());

        assertThat(criteria).containsExactly(SearchQuery.address(AddressType.Cc, cc));
    }

    @Test
    public void filterConditionShouldMapWhenHasAttachment() {
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
            .hasAttachment(true)
            .build());

        assertThat(criteria).containsExactly(SearchQuery.hasAttachment());
    }

    @Test
    public void filterConditionShouldMapWhenHasNoAttachment() {
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
            .hasAttachment(false)
            .build());

        assertThat(criteria).containsExactly(SearchQuery.hasNoAttachment());
    }

    @Test
    public void filterConditionShouldMapWhenBcc() {
        String bcc = "blindcopy@james.org";
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
                .bcc(bcc)
                .build());

        assertThat(criteria).containsExactly(SearchQuery.address(AddressType.Bcc, bcc));
    }

    @Test
    public void filterConditionShouldMapWhenSubject() {
        String subject = "subject";
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
                .subject(subject)
                .build());

        assertThat(criteria).containsExactly(SearchQuery.subject(subject));
    }

    @Test
    public void filterConditionShouldMapWhenBody() {
        String body = "body";
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
                .body(body)
                .build());

        assertThat(criteria).containsExactly(SearchQuery.bodyContains(body));
    }

    @Test
    public void filterConditionShouldMapWhenAttachments() {
        String attachments = "attachments";
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
                .attachments(attachments)
                .build());

        assertThat(criteria).containsExactly(SearchQuery.attachmentContains(attachments));
    }

    @Test
    public void filterConditionShouldMapWhenText() {
        String text = "text";
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
                .text(text)
                .build());

        assertThat(criteria).containsExactly(SearchQuery.or(ImmutableList.of(
            SearchQuery.address(AddressType.From, text),
            SearchQuery.address(AddressType.To, text),
            SearchQuery.address(AddressType.Cc, text),
            SearchQuery.address(AddressType.Bcc, text),
            SearchQuery.subject(text),
            SearchQuery.bodyContains(text),
            SearchQuery.attachmentContains(text),
            SearchQuery.attachmentFileName(text))));
    }

    @Test
    public void filterConditionShouldMapWhenAfter() {
        ZonedDateTime after = ZonedDateTime.now();
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
                .after(after)
                .build());

        assertThat(criteria).containsExactly(SearchQuery.sentDateAfter(Date.from(after.toInstant()), DateResolution.Second));
    }

    @Test
    public void filterConditionShouldMapWhenBefore() {
        ZonedDateTime before = ZonedDateTime.now();
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
                .before(before)
                .build());

        assertThat(criteria).containsExactly(SearchQuery.sentDateBefore(Date.from(before.toInstant()), DateResolution.Second));
    }

    @Test
    public void filterConditionShouldMapWhenIsAnswered() {
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
                .isAnswered(true)
                .build());

        assertThat(criteria).containsExactly(SearchQuery.flagIsSet(Flag.ANSWERED));
    }

    @Test
    public void filterConditionShouldMapWhenIsDraft() {
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
                .isDraft(true)
                .build());

        assertThat(criteria).containsExactly(SearchQuery.flagIsSet(Flag.DRAFT));
    }

    @Test
    public void filterConditionShouldMapWhenIsFlagged() {
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
                .isFlagged(true)
                .build());

        assertThat(criteria).containsExactly(SearchQuery.flagIsSet(Flag.FLAGGED));
    }

    @Test
    public void filterConditionShouldMapWhenIsUnread() {
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
                .isUnread(true)
                .build());

        assertThat(criteria).containsExactly(SearchQuery.flagIsUnSet(Flag.SEEN));
    }


    @Test
    public void filterConditionShouldMapWhenIsNotAnswered() {
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
            .isAnswered(false)
            .build());

        assertThat(criteria).containsExactly(SearchQuery.flagIsUnSet(Flag.ANSWERED));
    }

    @Test
    public void filterConditionShouldMapWhenIsNotDraft() {
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
            .isDraft(false)
            .build());

        assertThat(criteria).containsExactly(SearchQuery.flagIsUnSet(Flag.DRAFT));
    }

    @Test
    public void filterConditionShouldMapWhenIsNotFlagged() {
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
            .isFlagged(false)
            .build());

        assertThat(criteria).containsExactly(SearchQuery.flagIsUnSet(Flag.FLAGGED));
    }

    @Test
    public void filterConditionShouldMapWhenIsRead() {
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
            .isUnread(false)
            .build());

        assertThat(criteria).containsExactly(SearchQuery.flagIsSet(Flag.SEEN));
    }

    @Test
    public void filterConditionShouldMapWhenMaxSize() {
        int maxSize = 123;
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
                .maxSize(maxSize)
                .build());

        assertThat(criteria).containsExactly(SearchQuery.sizeLessThan(maxSize));
    }

    @Test
    public void filterConditionShouldMapWhenMinSize() {
        int minSize = 4;
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
                .minSize(minSize)
                .build());

        assertThat(criteria).containsExactly(SearchQuery.sizeGreaterThan(minSize));
    }

    @Test
    public void filterConditionShouldMapWhenHeaderWithOneElement() {
        String headerName = "name";
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
                .header(Header.from(ImmutableList.of(headerName)))
                .build());

        assertThat(criteria).containsExactly(SearchQuery.headerExists(headerName));
    }

    @Test
    public void filterConditionShouldMapWhenHeaderWithTwoElements() {
        String headerName = "name";
        String headerValue = "value";

        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
                .header(Header.from(ImmutableList.of(headerName, headerValue)))
                .build());

        assertThat(criteria).containsExactly(SearchQuery.headerContains(headerName, headerValue));
    }

    @Test
    public void filterConditionShouldMapTwoConditions() {
        String from = "sender@james.org";
        String to = "recipient@james.org";
        Filter filter = FilterOperator.and(
                FilterCondition.builder()
                    .from(from)
                    .build(),
                FilterCondition.builder()
                    .to(to)
                    .build());

        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(filter);

        assertThat(criteria).containsExactly(SearchQuery.and(ImmutableList.of(
            SearchQuery.address(AddressType.From, from),
            SearchQuery.address(AddressType.To, to))));
    }

    @Test
    public void filterConditionShouldMapWhenAndOperator() {
        String from = "sender@james.org";
        String to = "recipient@james.org";
        String subject = "subject";

        Filter complexFilter = FilterOperator.and(
                FilterCondition.builder()
                    .from(from)
                    .to(to)
                    .subject(subject)
                    .build());

        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(complexFilter);

        assertThat(criteria).containsExactly(SearchQuery.and(ImmutableList.of(
            SearchQuery.address(AddressType.From, from),
            SearchQuery.address(AddressType.To, to),
            SearchQuery.subject(subject))));
    }

    @Test
    public void filterConditionShouldMapWhenOrOperator() {
        String from = "sender@james.org";
        String to = "recipient@james.org";
        String subject = "subject";

        Filter complexFilter = FilterOperator.or(
                FilterCondition.builder()
                    .from(from)
                    .to(to)
                    .subject(subject)
                    .build());

        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(complexFilter);

        assertThat(criteria).containsExactly(SearchQuery.or(ImmutableList.of(
            SearchQuery.address(AddressType.From, from),
            SearchQuery.address(AddressType.To, to),
            SearchQuery.subject(subject))));
    }

    @Test
    public void filterConditionShouldMapWhenNotOperator() {
        String from = "sender@james.org";
        String to = "recipient@james.org";
        String subject = "subject";

        Filter complexFilter = FilterOperator.not(
                FilterCondition.builder()
                    .from(from)
                    .to(to)
                    .subject(subject)
                    .build());

        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(complexFilter);

        assertThat(criteria).containsExactly(SearchQuery.not(ImmutableList.of(
            SearchQuery.address(AddressType.From, from),
            SearchQuery.address(AddressType.To, to),
            SearchQuery.subject(subject))));
    }

    @Test
    public void filterConditionShouldMapWhenComplexFilterTree() {
        String from = "sender@james.org";
        String to = "recipient@james.org";
        String cc = "copy@james.org";

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
                        .build()));

        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(complexFilter);

        assertThat(criteria).containsExactly(SearchQuery.and(ImmutableList.of(
            SearchQuery.address(AddressType.From, from),
            SearchQuery.or(ImmutableList.of(
                SearchQuery.not(SearchQuery.address(AddressType.To, to)),
                SearchQuery.address(AddressType.Cc, cc))))));
    }

    @Test
    public void filterConditionShouldMapWhenHasKeyword() {
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
                .hasKeyword(Optional.of("$Flagged"))
                .build());

        assertThat(criteria).containsExactly(SearchQuery.flagIsSet(Flag.FLAGGED));
    }

    @Test
    public void filterConditionShouldMapWhenHasKeywordWithUserFlag() {
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
                .hasKeyword(Optional.of(FORWARDED))
                .build());

        assertThat(criteria).containsExactly(SearchQuery.flagIsSet(FORWARDED));
    }

    @Test
    public void filterConditionShouldMapWhenNotKeyword() {
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
            .notKeyword(Optional.of("$Flagged"))
            .build());

        assertThat(criteria).containsExactly(SearchQuery.flagIsUnSet(Flag.FLAGGED));
    }

    @Test
    public void filterConditionShouldMapWhenNotKeywordWithUserFlag() {
        Stream<SearchQuery.Criterion> criteria = new FilterToCriteria().convert(FilterCondition.builder()
                .notKeyword(Optional.of(FORWARDED))
                .build());

        assertThat(criteria).containsExactly(SearchQuery.flagIsUnSet(FORWARDED));
    }

    @Test
    public void attachmentFileNameShouldMapWhenHasAttachmentFileName() {
        String fileName = "file.gz";

        assertThat(new FilterToCriteria().convert(FilterCondition.builder()
            .attachmentFileName(Optional.of(fileName))
            .build()))
            .containsExactly(SearchQuery.attachmentFileName(fileName));
    }
}
