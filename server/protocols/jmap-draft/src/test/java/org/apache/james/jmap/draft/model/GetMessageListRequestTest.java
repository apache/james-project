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

package org.apache.james.jmap.draft.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.NotImplementedException;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import org.apache.james.jmap.model.Number;

public class GetMessageListRequestTest {

    @Test(expected = IllegalArgumentException.class)
    public void builderShouldThrowWhenPositionIsNegative() {
        GetMessageListRequest.builder().position(-1L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void builderShouldThrowWhenLimitIsNegative() {
        GetMessageListRequest.builder().limit(-1);
    }

    @Test(expected = NotImplementedException.class)
    public void builderShouldThrowWhenAccountId() {
        GetMessageListRequest.builder().accountId(null);
    }

    @Test(expected = NotImplementedException.class)
    public void builderShouldThrowWhenAnchor() {
        GetMessageListRequest.builder().anchor(null);
    }

    @Test(expected = NotImplementedException.class)
    public void builderShouldThrowWhenAnchorOffset() {
        GetMessageListRequest.builder().anchorOffset(0);
    }

    @Test(expected = NotImplementedException.class)
    public void builderShouldThrowWhenFetchThreads() {
        GetMessageListRequest.builder().fetchThreads(false);
    }

    @Test(expected = NotImplementedException.class)
    public void builderShouldThrowWhenFetchSearchSnippets() {
        GetMessageListRequest.builder().fetchSearchSnippets(false);
    }

    @Test
    public void builderShouldWork() {
        FilterCondition filterCondition = FilterCondition.builder()
                .inMailboxes(Optional.of(ImmutableList.of("1", "2")))
                .build();
        List<String> sort = ImmutableList.of("date desc");
        List<String> fetchMessageProperties = ImmutableList.of("id", "blobId");
        GetMessageListRequest expectedGetMessageListRequest = new GetMessageListRequest(Optional.empty(), Optional.of(filterCondition), sort, Optional.of(true), Optional.of(Number.fromLong(1L)), Optional.empty(), Optional.empty(), Optional.of(Number.fromLong(2)),
                Optional.empty(), Optional.of(true), fetchMessageProperties, Optional.empty());

        GetMessageListRequest getMessageListRequest = GetMessageListRequest.builder()
            .filter(filterCondition)
            .sort(sort)
            .collapseThreads(true)
            .position(1L)
            .limit(2)
            .fetchMessages(true)
            .fetchMessageProperties(fetchMessageProperties)
            .build();

        assertThat(getMessageListRequest).isEqualToComparingFieldByField(expectedGetMessageListRequest);
    }
}
