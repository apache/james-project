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

package org.apache.james.jmap.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.Test;

import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;

public class FilterConditionTest {

    @Test
    public void buildShouldWorkWhenNoInMailboxes() {
        FilterCondition filterCondition = FilterCondition.builder().build();
        assertThat(filterCondition.getInMailboxes()).isEmpty();
    }

    @Test
    public void buildShouldWorkWhenGivenInMailboxes() {
        FilterCondition filterCondition = FilterCondition.builder()
                .inMailboxes(Optional.of(ImmutableList.of("1", "2")))
                .build();
        assertThat(filterCondition.getInMailboxes()).contains(ImmutableList.of("1", "2"));
    }

    @Test
    public void buildShouldWorkWhenGivenInMailboxesAsEllipsis() {
        FilterCondition filterCondition = FilterCondition.builder()
                .inMailboxes("1", "2")
                .build();
        assertThat(filterCondition.getInMailboxes()).contains(ImmutableList.of("1", "2"));
    }

    @Test
    public void builderShouldBuildWhenGivenNotInMailboxes() {
        FilterCondition filterCondition = FilterCondition.builder()
                .notInMailboxes(Optional.of(ImmutableList.of("1", "2")))
                .build();
        assertThat(filterCondition.getNotInMailboxes()).contains(ImmutableList.of("1", "2"));
    }

    @Test
    public void builderShouldBuildWhenGivenNotInMailboxesAsEllipsis() {
        FilterCondition filterCondition = FilterCondition.builder()
                .notInMailboxes("1", "2")
                .build();
        assertThat(filterCondition.getNotInMailboxes()).contains(ImmutableList.of("1", "2"));
    }

    @Test
    public void buildShouldWork() {
        ZonedDateTime before = ZonedDateTime.parse("2016-07-19T14:30:00Z");
        ZonedDateTime after = ZonedDateTime.parse("2016-07-19T14:31:00Z");
        int minSize = 4;
        int maxSize = 123;
        boolean isFlagged = true;
        boolean isUnread = true;
        boolean isAnswered = true;
        boolean isDraft = true;
        boolean hasAttachment = true;
        String text = "text";
        String from = "sender@james.org";
        String to = "recipient@james.org";
        String cc = "copy@james.org";
        String bcc = "blindcopy@james.org";
        String subject = "subject";
        String body = "body";
        Header header = Header.from(ImmutableList.of("name", "value"));
        FilterCondition expectedFilterCondition = new FilterCondition(Optional.of(ImmutableList.of("1")), Optional.of(ImmutableList.of("2")), Optional.of(before), Optional.of(after), Optional.of(minSize), Optional.of(maxSize), 
                Optional.of(isFlagged), Optional.of(isUnread), Optional.of(isAnswered), Optional.of(isDraft), Optional.of(hasAttachment), Optional.of(text), Optional.of(from), 
                Optional.of(to), Optional.of(cc), Optional.of(bcc), Optional.of(subject), Optional.of(body), Optional.of(header));

        FilterCondition filterCondition = FilterCondition.builder()
                .inMailboxes(Optional.of(ImmutableList.of("1")))
                .notInMailboxes("2")
                .before(before)
                .after(after)
                .minSize(minSize)
                .maxSize(maxSize)
                .isFlagged(isFlagged)
                .isUnread(isUnread)
                .isAnswered(isAnswered)
                .isDraft(isDraft)
                .hasAttachment(hasAttachment)
                .text(text)
                .from(from)
                .to(to)
                .cc(cc)
                .bcc(bcc)
                .subject(subject)
                .body(body)
                .header(header)
                .build();

        assertThat(filterCondition).isEqualToComparingFieldByField(expectedFilterCondition);
    }

    @Test
    public void shouldRespectJavaBeanContract() {
        EqualsVerifier.forClass(FilterCondition.class).verify();
    }
}
