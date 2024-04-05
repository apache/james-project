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
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import org.apache.james.jmap.model.Number;

public class GetMailboxMessageListResponseTest {

    @Test(expected = NotImplementedException.class)
    public void builderShouldThrowWhenAccountId() {
        GetMessageListResponse.builder().accountId(null);
    }

    @Test(expected = NotImplementedException.class)
    public void builderShouldThrowWhenCollapseThreads() {
        GetMessageListResponse.builder().collapseThreads(false);
    }

    @Test(expected = NotImplementedException.class)
    public void builderShouldThrowWhenState() {
        GetMessageListResponse.builder().state(null);
    }

    @Test(expected = NotImplementedException.class)
    public void builderShouldThrowWhenCanCalculateUpdates() {
        GetMessageListResponse.builder().canCalculateUpdates(false);
    }

    @Test(expected = NotImplementedException.class)
    public void builderShouldThrowWhenPosition() {
        GetMessageListResponse.builder().position(0);
    }

    @Test(expected = NotImplementedException.class)
    public void builderShouldThrowWhenTotal() {
        GetMessageListResponse.builder().total(0);
    }

    @Test(expected = NotImplementedException.class)
    public void builderShouldThrowWhenThreadIds() {
        GetMessageListResponse.builder().threadIds(null);
    }
    
    @Test
    public void builderShouldWork() {
        FilterCondition filterCondition = FilterCondition.builder()
                .inMailboxes(Optional.of(ImmutableList.of("1", "2")))
                .build();
        List<String> sort = ImmutableList.of("date desc");
        List<MessageId> messageIds = ImmutableList.of(TestMessageId.of(3), TestMessageId.of(4));
        GetMessageListResponse expectedGetMessageListResponse = new GetMessageListResponse(null, filterCondition, sort, false, null, false,
            Number.ZERO, Number.ZERO, ImmutableList.of(), messageIds);

        GetMessageListResponse getMessageListResponse = GetMessageListResponse.builder()
            .filter(filterCondition)
            .sort(sort)
            .messageIds(messageIds)
            .build();
        assertThat(getMessageListResponse).isEqualToComparingFieldByField(expectedGetMessageListResponse);
    }
}
