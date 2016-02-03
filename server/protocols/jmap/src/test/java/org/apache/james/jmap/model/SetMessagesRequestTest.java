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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.apache.commons.lang.NotImplementedException;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class SetMessagesRequestTest {

    @Test
    public void builderShouldThrowWhenAccountIdIsNotNull() {
        assertThatThrownBy(() -> SetMessagesRequest.builder().accountId(""))
            .isInstanceOf(NotImplementedException.class);
    }

    @Test
    public void builderShouldThrowWhenIfInStateIsNotNull() {
        assertThatThrownBy(() -> SetMessagesRequest.builder().ifInState(""))
            .isInstanceOf(NotImplementedException.class);
    }

    @Test
    public void builderShouldThrowWhenCreateIsNotEmpty() {
        assertThatThrownBy(() -> SetMessagesRequest.builder().create(ImmutableList.of(Message.builder()
                .id(MessageId.of("user|create|1"))
                .blobId("blobId")
                .threadId("threadId")
                .mailboxIds(ImmutableList.of("mailboxId"))
                .headers(ImmutableMap.of("key", "value"))
                .subject("subject")
                .size(123)
                .date(ZonedDateTime.now())
                .preview("preview")
                .build())))
            .isInstanceOf(NotImplementedException.class);
    }

    @Test
    public void builderShouldWork() {
        ImmutableList<MessageId> destroy = ImmutableList.of(MessageId.of("user|destroy|1"));

        SetMessagesRequest expected = new SetMessagesRequest(Optional.empty(), Optional.empty(), ImmutableList.of(), ImmutableMap.of(), destroy);

        SetMessagesRequest setMessagesRequest = SetMessagesRequest.builder()
            .accountId(null)
            .ifInState(null)
            .create(ImmutableList.of())
            .update(ImmutableMap.of())
            .destroy(destroy)
            .build();

        assertThat(setMessagesRequest).isEqualToComparingFieldByField(expected);
    }
}
