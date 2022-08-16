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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.apache.james.webadmin.httpclient.feign.DomainFeignClient;
import org.apache.james.webadmin.httpclient.feign.JamesFeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import feign.Request;
import feign.Response;

public class DomainClientTest {
    private DomainClient testee;
    private DomainFeignClient feignClient;

    @BeforeEach
    void setup() {
        feignClient = mock(DomainFeignClient.class);
        testee = new DomainClient(feignClient);
    }

    @Test
    void createADomainShouldSuccessWhenResponseReturn204() {
        when(feignClient.createADomain(any())).thenReturn(Response.builder()
            .status(204)
            .request(mock(Request.class))
            .body("", StandardCharsets.UTF_8)
            .build());
        assertThatCode(() -> testee.createADomain("domain"))
            .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 409, 500})
    void createADomainShouldThrowWhenResponseReturnIsNot204(int responseCode) {
        when(feignClient.createADomain(any())).thenReturn(Response.builder()
            .status(responseCode)
            .body("", StandardCharsets.UTF_8)
            .request(mock(Request.class))
            .build());
        assertThatThrownBy(() -> testee.createADomain("domain"))
            .isInstanceOf(JamesFeignException.class);
    }

    @Test
    void deleteADomainShouldSuccessWhenResponseReturn204() {
        when(feignClient.deleteADomain(any())).thenReturn(Response.builder()
            .status(204)
            .request(mock(Request.class))
            .body("", StandardCharsets.UTF_8)
            .build());
        assertThatCode(() -> testee.deleteADomain("domain"))
            .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 409, 500})
    void deleteADomainShouldThrowWhenResponseReturnIsNot204(int responseCode) {
        when(feignClient.deleteADomain(any())).thenReturn(Response.builder()
            .status(responseCode)
            .body("", StandardCharsets.UTF_8)
            .request(mock(Request.class))
            .build());
        assertThatThrownBy(() -> testee.deleteADomain("domain"))
            .isInstanceOf(JamesFeignException.class);
    }


    @Test
    void doesExistShouldReturnTrueWhenResponse204() {
        when(feignClient.doesExist(any())).thenReturn(Response.builder()
            .status(204)
            .body("", StandardCharsets.UTF_8)
            .request(mock(Request.class))
            .build());

        assertThat(testee.doesExist("domain"))
            .isTrue();
    }

    @Test
    void doesExistShouldReturnFalseWhenResponse404() {
        when(feignClient.doesExist(any())).thenReturn(Response.builder()
            .status(404)
            .body("", StandardCharsets.UTF_8)
            .request(mock(Request.class))
            .build());

        assertThat(testee.doesExist("domain"))
            .isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 409, 500})
    void doesExistShouldThrowWhenResponseIsNot200Or404(int responseCode) {
        when(feignClient.doesExist(any())).thenReturn(Response.builder()
            .status(responseCode)
            .body("", StandardCharsets.UTF_8)
            .request(mock(Request.class))
            .build());
        assertThatThrownBy(() -> testee.doesExist("domain"))
            .isInstanceOf(JamesFeignException.class);
    }

    @Test
    void deleteADomainAliasShouldSuccessWhenResponseReturn204() {
        when(feignClient.deleteADomainAlias(any(), any())).thenReturn(Response.builder()
            .status(204)
            .request(mock(Request.class))
            .body("", StandardCharsets.UTF_8)
            .build());
        assertThatCode(() -> testee.deleteADomainAlias("destinationDomain", "sourceDomain"))
            .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 409, 500})
    void deleteADomainAliasShouldThrowWhenResponseReturnIsNot204(int responseCode) {
        when(feignClient.deleteADomainAlias(any(), any())).thenReturn(Response.builder()
            .status(responseCode)
            .body("", StandardCharsets.UTF_8)
            .request(mock(Request.class))
            .build());
        assertThatThrownBy(() -> testee.deleteADomainAlias("destinationDomain", "sourceDomain"))
            .isInstanceOf(JamesFeignException.class);
    }

    @Test
    void addADomainAliasShouldSuccessWhenResponseReturn204() {
        when(feignClient.addADomainAlias(any(), any())).thenReturn(Response.builder()
            .status(204)
            .body("", StandardCharsets.UTF_8)
            .request(mock(Request.class))
            .build());
        assertThatCode(() -> testee.addADomainAlias("destinationDomain", "sourceDomain"))
            .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 409, 500})
    void addADomainAliasShouldThrowWhenResponseReturnIsNot204(int responseCode) {
        when(feignClient.addADomainAlias(any(), any())).thenReturn(Response.builder()
            .status(responseCode)
            .body("", StandardCharsets.UTF_8)
            .request(mock(Request.class))
            .build());
        assertThatThrownBy(() -> testee.addADomainAlias("destinationDomain", "sourceDomain"))
            .isInstanceOf(JamesFeignException.class);
    }
}
