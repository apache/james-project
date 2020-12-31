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
package org.apache.james.webadmin.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.apache.james.util.streams.Limit;
import org.apache.james.util.streams.Offset;
import org.junit.jupiter.api.Test;

import spark.HaltException;
import spark.Request;

class ParametersExtractorTest {

    @Test
    void extractLimitShouldReturnUnlimitedWhenNotInParameters() {
        Request request = mock(Request.class);
        when(request.queryParams("limit"))
            .thenReturn(null);

        Limit limit = ParametersExtractor.extractLimit(request);

        assertThat(limit).isEqualTo(Limit.unlimited());
    }

    @Test
    void extractLimitShouldReturnUnlimitedWhenPresentInParametersButEmpty() {
        Request request = mock(Request.class);
        when(request.queryParams("limit"))
            .thenReturn("");

        Limit limit = ParametersExtractor.extractLimit(request);

        assertThat(limit).isEqualTo(Limit.unlimited());
    }

    @Test
    void extractLimitShouldReturnTheLimitWhenPresentInParameters() {
        Request request = mock(Request.class);
        when(request.queryParams("limit"))
            .thenReturn("123");

        Limit limit = ParametersExtractor.extractLimit(request);

        assertThat(limit).isEqualTo(Limit.from(123));
    }

    @Test
    void extractLimitShouldThrowWhenNegativeLimit() {
        Request request = mock(Request.class);
        when(request.queryParams("limit"))
            .thenReturn("-123");

        assertThatThrownBy(() -> ParametersExtractor.extractLimit(request))
            .isInstanceOf(HaltException.class);
    }

    @Test
    void extractLimitShouldThrowWhenZeroLimit() {
        Request request = mock(Request.class);
        when(request.queryParams("limit"))
            .thenReturn("0");

        assertThatThrownBy(() -> ParametersExtractor.extractLimit(request))
            .isInstanceOf(HaltException.class);
    }

    @Test
    void extractOffsetShouldReturnNoneWhenNotInParameters() {
        Request request = mock(Request.class);
        when(request.queryParams("offset"))
            .thenReturn(null);

        Offset offset = ParametersExtractor.extractOffset(request);

        assertThat(offset).isEqualTo(Offset.none());
    }

    @Test
    void extractOffsetShouldReturnNoneWhenPresentInParametersButEmpty() {
        Request request = mock(Request.class);
        when(request.queryParams("offset"))
            .thenReturn("");

        Offset offset = ParametersExtractor.extractOffset(request);

        assertThat(offset).isEqualTo(Offset.none());
    }

    @Test
    void extractOffsetShouldReturnTheOffsetWhenPresentInParameters() {
        Request request = mock(Request.class);
        when(request.queryParams("offset"))
            .thenReturn("123");

        Offset offset = ParametersExtractor.extractOffset(request);

        assertThat(offset).isEqualTo(Offset.from(123));
    }

    @Test
    void extractOffsetShouldThrowWhenNegativeOffset() {
        Request request = mock(Request.class);
        when(request.queryParams("offset"))
            .thenReturn("-123");

        assertThatThrownBy(() -> ParametersExtractor.extractOffset(request))
            .isInstanceOf(HaltException.class);
    }

    @Test
    void extractOffsetShouldReturnNoneWhenZeroLimit() {
        Request request = mock(Request.class);
        when(request.queryParams("offset"))
            .thenReturn("0");

        Offset offset = ParametersExtractor.extractOffset(request);

        assertThat(offset).isEqualTo(Offset.none());
    }
    
    

    @Test
    void extractPositiveDoubleShouldReturnEmptyWhenNotInParameters() {
        String parameterName = "param";
        Request request = mock(Request.class);
        when(request.queryParams(parameterName))
            .thenReturn(null);

        Optional<Double> result = ParametersExtractor.extractPositiveDouble(request, parameterName);

        assertThat(result).isEmpty();
    }

    @Test
    void extractPositiveDoubleShouldReturnNoneWhenPresentInParametersButEmpty() {
        String parameterName = "param";
        Request request = mock(Request.class);
        when(request.queryParams(parameterName))
            .thenReturn("");

        Optional<Double> result = ParametersExtractor.extractPositiveDouble(request, parameterName);

        assertThat(result).isEmpty();
    }

    @Test
    void extractPositiveDoubleShouldReturnTheDoubleWhenPresentInParameters() {
        String parameterName = "param";
        Request request = mock(Request.class);
        when(request.queryParams(parameterName))
            .thenReturn("123");

        Optional<Double> result = ParametersExtractor.extractPositiveDouble(request, parameterName);

        assertThat(result).contains(123d);
    }

    @Test
    void extractPositiveDoubleShouldThrowWhenNegativePositiveDouble() {
        String parameterName = "param";
        Request request = mock(Request.class);
        when(request.queryParams(parameterName))
            .thenReturn("-123");

        assertThatThrownBy(() -> {
            ParametersExtractor.extractPositiveDouble(request, parameterName);
        })
            .isInstanceOf(HaltException.class);
    }

    @Test
    void extractPositiveDoubleShouldReturnZeroWhenZeroLimit() {
        String parameterName = "param";
        Request request = mock(Request.class);
        when(request.queryParams(parameterName))
            .thenReturn("0");

        Optional<Double> result = ParametersExtractor.extractPositiveDouble(request, parameterName);

        assertThat(result).contains(0d);
    }
}
