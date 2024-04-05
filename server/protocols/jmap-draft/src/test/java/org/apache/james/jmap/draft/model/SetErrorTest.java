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

import static org.apache.james.jmap.model.MessageProperties.MessageProperty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.Set;

import org.junit.Test;

import com.google.common.collect.ImmutableSet;

public class SetErrorTest {

    @Test
    public void buildShouldThrowWhenTypeIsNotGiven() {
        assertThatThrownBy(() -> SetError.builder().build())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void buildShouldWorkWhenAllMandatoryFieldsAreGiven() {
        SetError expected = new SetError(SetError.Type.ERROR, Optional.empty(), Optional.empty());

        SetError setError = SetError.builder()
            .type(SetError.Type.ERROR)
            .build();

        assertThat(setError).isEqualToComparingFieldByField(expected);
    }

    @Test
    public void buildShouldWorkWhenAllFieldsAreGiven() {
        ImmutableSet<MessageProperty> props = ImmutableSet.of(MessageProperty.attachedMessages);

        SetError expected = new SetError(SetError.Type.ERROR, Optional.of("description"), Optional.of(props));

        SetError setError = SetError.builder()
                .type(SetError.Type.ERROR)
                .description("description")
                .properties(ImmutableSet.of(MessageProperty.attachedMessages))
                .build();

        assertThat(setError).isEqualToComparingFieldByField(expected);
    }

    @Test
    public void buildShouldMergePassedProperties() {
        SetError result = SetError.builder()
                .type(SetError.Type.ERROR).description("a description")
                .properties(ImmutableSet.of(MessageProperty.bcc))
                .properties(ImmutableSet.of(MessageProperty.cc))
                .build();

        assertThat(result.getProperties()).contains(ImmutableSet.of(MessageProperty.bcc, MessageProperty.cc));
    }

    @Test
    public void buildShouldDefaultToEmptyWhenPropertiesOmitted() {
        SetError result = SetError.builder()
                .type(SetError.Type.ERROR).description("a description")
                .build();

        assertThat(result.getProperties()).isEmpty();
    }

    @Test
    public void buildShouldDefaultToEmptyWhenPropertiesNull() {
        SetError result = SetError.builder()
                .type(SetError.Type.ERROR).description("a description").properties((Set<MessageProperty>)null)
                .build();

        assertThat(result.getProperties()).isPresent();
        assertThat(result.getProperties().get()).isEmpty();
    }

    @Test
    public void buildShouldBeIdempotentWhenNullPropertiesSet() {
        ImmutableSet<MessageProperty> nonNullProperty = ImmutableSet.of(MessageProperty.from);
        SetError result = SetError.builder()
                .type(SetError.Type.ERROR).description("a description")
                .properties(nonNullProperty)
                .properties((Set<MessageProperty>)null)
                .build();

        assertThat(result.getProperties()).isPresent();
        assertThat(result.getProperties().get()).isEqualTo(nonNullProperty);
    }

    @Test
    public void buildShouldDefaultToEmptyWhenPropertiesWithNoArgument() {
        SetError result = SetError.builder()
                .type(SetError.Type.ERROR).description("a description").properties()
                .build();

        assertThat(result.getProperties()).isPresent();
        assertThat(result.getProperties().get()).isEmpty();
    }

    @Test
    public void buildShouldBeIdempotentWhenPropertiesWithNoArgument() {
        ImmutableSet<MessageProperty> nonNullProperty = ImmutableSet.of(MessageProperty.from);
        SetError result = SetError.builder()
                .type(SetError.Type.ERROR).description("a description")
                .properties(nonNullProperty)
                .properties()
                .build();

        assertThat(result.getProperties()).isPresent();
        assertThat(result.getProperties().get()).isEqualTo(nonNullProperty);
    }

}