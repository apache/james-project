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

package org.apache.james.webadmin.httpclient;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.apache.james.webadmin.httpclient.feign.JamesFeignException;
import org.apache.james.webadmin.httpclient.feign.QuotaFeignClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import feign.Request;
import feign.Response;

public class QuotaClientTest {

    private QuotaClient testee;
    private QuotaFeignClient feignClient;

    @BeforeEach
    void setup() {
        feignClient = mock(QuotaFeignClient.class);
        testee = new QuotaClient(feignClient);
    }

    @Test
    void setQuotaCountShouldSuccessWhenResponseReturn204() {
        when(feignClient.setQuotaCount(any())).thenReturn(Response.builder()
            .status(204)
            .request(mock(Request.class))
            .body("", StandardCharsets.UTF_8)
            .build());
        assertThatCode(() -> testee.setQuotaCount(1L))
            .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 409, 500})
    void setQuotaCountShouldThrowWhenResponseReturnIsNot204(int responseCode) {
        when(feignClient.setQuotaCount(any())).thenReturn(Response.builder()
            .status(responseCode)
            .body("", StandardCharsets.UTF_8)
            .request(mock(Request.class))
            .build());
        assertThatThrownBy(() -> testee.setQuotaCount(1L))
            .isInstanceOf(JamesFeignException.class);
    }

    @Test
    void setQuotaSizeShouldSuccessWhenResponseReturn204() {
        when(feignClient.setQuotaSize(any())).thenReturn(Response.builder()
            .status(204)
            .request(mock(Request.class))
            .body("", StandardCharsets.UTF_8)
            .build());
        assertThatCode(() -> testee.setQuotaSize(1L))
            .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 409, 500})
    void setQuotaSizeShouldThrowWhenResponseReturnIsNot204(int responseCode) {
        when(feignClient.setQuotaSize(any())).thenReturn(Response.builder()
            .status(responseCode)
            .body("", StandardCharsets.UTF_8)
            .request(mock(Request.class))
            .build());
        assertThatThrownBy(() -> testee.setQuotaSize(1L))
            .isInstanceOf(JamesFeignException.class);
    }

    @Test
    void deleteQuotaCountShouldSuccessWhenResponseReturn204() {
        when(feignClient.deleteQuotaCount()).thenReturn(Response.builder()
            .status(204)
            .request(mock(Request.class))
            .body("", StandardCharsets.UTF_8)
            .build());
        assertThatCode(() -> testee.deleteQuotaCount())
            .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 409, 500})
    void deleteQuotaCountShouldThrowWhenResponseReturnIsNot204(int responseCode) {
        when(feignClient.deleteQuotaCount()).thenReturn(Response.builder()
            .status(responseCode)
            .body("", StandardCharsets.UTF_8)
            .request(mock(Request.class))
            .build());
        assertThatThrownBy(() -> testee.deleteQuotaCount())
            .isInstanceOf(JamesFeignException.class);
    }

    @Test
    void deleteQuotaSizeShouldSuccessWhenResponseReturn204() {
        when(feignClient.deleteQuotaSize()).thenReturn(Response.builder()
            .status(204)
            .request(mock(Request.class))
            .body("", StandardCharsets.UTF_8)
            .build());
        assertThatCode(() -> testee.deleteQuotaSize())
            .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 409, 500})
    void deleteQuotaSizeShouldThrowWhenResponseReturnIsNot204(int responseCode) {
        when(feignClient.deleteQuotaSize()).thenReturn(Response.builder()
            .status(responseCode)
            .body("", StandardCharsets.UTF_8)
            .request(mock(Request.class))
            .build());
        assertThatThrownBy(() -> testee.deleteQuotaSize())
            .isInstanceOf(JamesFeignException.class);
    }
}
