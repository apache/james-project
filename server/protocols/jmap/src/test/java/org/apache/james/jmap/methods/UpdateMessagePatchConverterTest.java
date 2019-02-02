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

package org.apache.james.jmap.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.james.jmap.model.UpdateMessagePatch;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableSet;

public class UpdateMessagePatchConverterTest {

    @Test
    public void fromJsonNodeShouldSetValidationResultWhenPatchIsInvalid() {

        UpdateMessagePatchValidator stubValidator = mock(UpdateMessagePatchValidator.class);
        when(stubValidator.isValid(any(ObjectNode.class))).thenReturn(false);
        ImmutableSet<ValidationResult> nonEmptyValidationResult = ImmutableSet.of(ValidationResult.builder().build());
        when(stubValidator.validate(any(ObjectNode.class))).thenReturn(nonEmptyValidationResult);

        UpdateMessagePatchConverter sut = new UpdateMessagePatchConverter(null, stubValidator);

        ObjectNode dummynode = JsonNodeFactory.instance.objectNode();
        UpdateMessagePatch result = sut.fromJsonNode(dummynode);

        assertThat(result).extracting(UpdateMessagePatch::getValidationErrors)
                .isNotEmpty();
    }

    @Test
    public void fromJsonNodeShouldReturnNoErrorWhenPatchIsValid() {

        UpdateMessagePatchValidator mockValidator = mock(UpdateMessagePatchValidator.class);
        when(mockValidator.isValid(any(ObjectNode.class))).thenReturn(true);
        when(mockValidator.validate(any(ObjectNode.class))).thenReturn(ImmutableSet.of());
        verify(mockValidator, never()).validate(any(ObjectNode.class));
        ObjectMapper jsonParser = new ObjectMapper();

        UpdateMessagePatchConverter sut = new UpdateMessagePatchConverter(jsonParser, mockValidator);

        ObjectNode dummynode = JsonNodeFactory.instance.objectNode();
        UpdateMessagePatch result = sut.fromJsonNode(dummynode);

        assertThat(result.getValidationErrors().isEmpty()).isTrue();
    }

}