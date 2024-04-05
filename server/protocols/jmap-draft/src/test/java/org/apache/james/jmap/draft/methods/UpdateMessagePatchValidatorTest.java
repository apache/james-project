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

package org.apache.james.jmap.draft.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Set;

import org.apache.james.jmap.draft.model.UpdateMessagePatch;
import org.apache.james.jmap.json.ObjectMapperFactory;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class UpdateMessagePatchValidatorTest {
    private ObjectMapperFactory objectMapperFactory;

    @Before
    public void setUp() {
        objectMapperFactory = mock(ObjectMapperFactory.class);
    }

    @Test
    public void validateShouldReturnPropertyNameWhenPropertyHasInvalidType() throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        when(objectMapperFactory.forParsing())
            .thenReturn(mapper);

        String jsonContent = "{ \"isUnread\" : \"123\" }";
        ObjectNode rootNode = mapper.readValue(jsonContent, ObjectNode.class);

        UpdateMessagePatchValidator sut = new UpdateMessagePatchValidator(objectMapperFactory);
        Set<ValidationResult> result = sut.validate(rootNode);
        assertThat(result).extracting(ValidationResult::getProperty).contains("isUnread");
    }

    @Test
    public void isValidShouldReturnTrueWhenPatchWellFormatted() throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        when(objectMapperFactory.forParsing())
            .thenReturn(mapper);

        String jsonContent = "{ \"isUnread\" : \"true\" }";
        ObjectNode rootNode = mapper.readValue(jsonContent, ObjectNode.class);

        UpdateMessagePatchValidator sut = new UpdateMessagePatchValidator(objectMapperFactory);
        assertThat(sut.isValid(rootNode)).isTrue();
    }

    @Test
    public void validateShouldReturnANonEmptyResultWhenParsingThrows() throws IOException {
        //Given
        ObjectNode emptyRootNode = new ObjectMapper().createObjectNode();

        ObjectMapper mapper = mock(ObjectMapper.class);
        JsonGenerator jsonGenerator = null;
        when(mapper.treeToValue(any(), eq(UpdateMessagePatch.class)))
            .thenThrow(JsonMappingException.from(jsonGenerator, "Exception when parsing"));

        when(objectMapperFactory.forParsing())
            .thenReturn(mapper);

        UpdateMessagePatchValidator sut = new UpdateMessagePatchValidator(objectMapperFactory);

        // When
        Set<ValidationResult> result = sut.validate(emptyRootNode);
        // Then
        assertThat(result).isNotEmpty();
        assertThat(result).extracting(ValidationResult::getProperty).contains(ValidationResult.UNDEFINED_PROPERTY);
    }

}