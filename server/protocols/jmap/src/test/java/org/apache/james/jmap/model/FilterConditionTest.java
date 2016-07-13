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

import java.util.Optional;

import org.apache.commons.lang.NotImplementedException;
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
    
    @Test(expected=NotImplementedException.class)
    public void builderShouldThrowWhenBefore() {
        FilterCondition.builder().before(null);
    }

    @Test(expected=NotImplementedException.class)
    public void builderShouldThrowWhenAfter() {
        FilterCondition.builder().after(null);
    }

    @Test(expected=NotImplementedException.class)
    public void builderShouldThrowWhenMinSize() {
        FilterCondition.builder().minSize(0);
    }

    @Test(expected=NotImplementedException.class)
    public void builderShouldThrowWhenMaxSize() {
        FilterCondition.builder().maxSize(0);
    }

    @Test(expected=NotImplementedException.class)
    public void builderShouldThrowWhenIsFlagged() {
        FilterCondition.builder().isFlagged(false);
    }

    @Test(expected=NotImplementedException.class)
    public void builderShouldThrowWhenIsUnread() {
        FilterCondition.builder().isUnread(false);
    }

    @Test(expected=NotImplementedException.class)
    public void builderShouldThrowWhenIsAnswered() {
        FilterCondition.builder().isAnswered(false);
    }

    @Test(expected=NotImplementedException.class)
    public void builderShouldThrowWhenIsDraft() {
        FilterCondition.builder().isDraft(false);
    }

    @Test(expected=NotImplementedException.class)
    public void builderShouldThrowWhenHasAttachment() {
        FilterCondition.builder().hasAttachment(false);
    }

    @Test(expected=NotImplementedException.class)
    public void builderShouldThrowWhenText() {
        FilterCondition.builder().text(null);
    }

    @Test(expected=NotImplementedException.class)
    public void builderShouldThrowWhenFrom() {
        FilterCondition.builder().from(null);
    }

    @Test(expected=NotImplementedException.class)
    public void builderShouldThrowWhenTo() {
        FilterCondition.builder().to(null);
    }

    @Test(expected=NotImplementedException.class)
    public void builderShouldThrowWhenCc() {
        FilterCondition.builder().cc(null);
    }

    @Test(expected=NotImplementedException.class)
    public void builderShouldThrowWhenBcc() {
        FilterCondition.builder().bcc(null);
    }

    @Test(expected=NotImplementedException.class)
    public void builderShouldThrowWhenSubject() {
        FilterCondition.builder().subject(null);
    }

    @Test(expected=NotImplementedException.class)
    public void builderShouldThrowWhenBody() {
        FilterCondition.builder().body(null);
    }

    @Test(expected=NotImplementedException.class)
    public void builderShouldThrowWhenHeader() {
        FilterCondition.builder().header(ImmutableList.of());
    }

    @Test
    public void buildShouldWork() {
        FilterCondition expectedFilterCondition = new FilterCondition(Optional.of(ImmutableList.of("1")), Optional.of(ImmutableList.of("2")), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        FilterCondition filterCondition = FilterCondition.builder()
                .inMailboxes(Optional.of(ImmutableList.of("1")))
                .notInMailboxes("2")
                .build();

        assertThat(filterCondition).isEqualToComparingFieldByField(expectedFilterCondition);
    }

    @Test
    public void shouldRespectJavaBeanContract() {
        EqualsVerifier.forClass(FilterCondition.class).verify();
    }
}
