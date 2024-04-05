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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.jmap.model.MessageProperties.HeaderProperty;
import org.apache.james.jmap.model.MessageProperties.MessageProperty;
import org.apache.james.mailbox.model.TestMessageId;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class GetMessagesRequestTest {

    @Test
    public void shouldAllowOptionalAccountId() {
        GetMessagesRequest result = GetMessagesRequest.builder()
                .ids(ImmutableList.of(TestMessageId.of(1)))
                .build();
        assertThat(result).isNotNull();
        assertThat(result.getAccountId()).isEmpty();
    }

    @Test
    public void shouldThrowWhenAccountIdIsNull() {
        assertThatThrownBy(() -> GetMessagesRequest.builder().accountId(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void shouldAllowEmptyMessagesList() {
        GetMessagesRequest result = GetMessagesRequest.builder()
                .accountId("accountId")
                .ids(ImmutableList.of())
                .build();
        assertThat(result).isNotNull();
        assertThat(result.getIds()).isEmpty();
    }

    @Test
    public void shouldAllowAbsentPropertyList() {
        GetMessagesRequest result = GetMessagesRequest.builder()
                .accountId("accountId")
                .ids(ImmutableList.of())
                .build();
        assertThat(result).isNotNull();
        assertThat(result.getProperties().getOptionalMessageProperties()).isEmpty();
        assertThat(result.getProperties().getOptionalHeadersProperties()).isEmpty();
    }

    @Test
    public void shouldAllowEmptyPropertyList() {
        GetMessagesRequest result = GetMessagesRequest.builder()
                .accountId("accountId")
                .ids(ImmutableList.of())
                .properties(ImmutableList.of())
                .build();
        assertThat(result).isNotNull();
        assertThat(result.getProperties().getOptionalMessageProperties()).hasValue(ImmutableSet.of());
        assertThat(result.getProperties().getOptionalHeadersProperties()).hasValue(ImmutableSet.of());
    }

    @Test
    public void shouldConvertPropertiesWhenMessageAndHeaderPropertiesAreGiven() {
        GetMessagesRequest result = GetMessagesRequest.builder()
                .accountId("accountId")
                .ids(ImmutableList.of())
                .properties(ImmutableList.of("id", "headers.subject", "threadId", "headers.test"))
                .build();
        assertThat(result).isNotNull();
        assertThat(result.getProperties().getOptionalMessageProperties()).hasValue(ImmutableSet.of(MessageProperty.id, MessageProperty.threadId));
        assertThat(result.getProperties().getOptionalHeadersProperties()).hasValue(ImmutableSet.of(HeaderProperty.valueOf("headers.subject"), HeaderProperty.valueOf("headers.test")));
    }
}
