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

import java.util.Optional;

import org.apache.james.jmap.methods.ErrorResponse;
import org.junit.Test;

public class ErrorResponseTest {

    @Test
    public void buildShouldReturnDefaultErrorWhenNoParameter() {
        ErrorResponse expected = new ErrorResponse(ErrorResponse.DEFAULT_ERROR_MESSAGE, Optional.empty());
        
        ErrorResponse actual = ErrorResponse
                .builder()
                .build();
        
        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

    @Test
    public void buildShouldReturnDefaultErrorWhenNullParameter() {
        ErrorResponse expected = new ErrorResponse(ErrorResponse.DEFAULT_ERROR_MESSAGE, Optional.empty());
        
        ErrorResponse actual = ErrorResponse
                .builder()
                .type(null)
                .build();
        
        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

    @Test
    public void buildShouldReturnDefinedErrorWhenTypeParameter() {
        ErrorResponse expected = new ErrorResponse("my error", Optional.empty());
        
        ErrorResponse actual = ErrorResponse
                .builder()
                .type("my error")
                .build();
        
        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

    @Test
    public void buildShouldReturnDefinedErrorWhenTypeParameterAndNullDescription() {
        ErrorResponse expected = new ErrorResponse("my error", Optional.empty());
        
        ErrorResponse actual = ErrorResponse
                .builder()
                .type("my error")
                .description(null)
                .build();
        
        assertThat(actual).isEqualToComparingFieldByField(expected);
    }

    @Test
    public void buildShouldReturnDefinedErrorWithDescriptionWhenTypeAndDescriptionParameters() {
        ErrorResponse expected = new ErrorResponse("my error", Optional.of("custom description"));
        
        ErrorResponse actual = ErrorResponse
                .builder()
                .type("my error")
                .description("custom description")
                .build();
        
        assertThat(actual).isEqualToComparingFieldByField(expected);
    }
}
