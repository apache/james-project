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

import org.apache.james.webadmin.httpclient.feign.JamesFeignException;
import org.apache.james.webadmin.httpclient.feign.UserFeignClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import feign.Request;
import feign.Response;

public class UserClientTest {

    private UserClient testee;
    private UserFeignClient feignClient;

    @BeforeEach
    void setup() {
        feignClient = mock(UserFeignClient.class);
        testee = new UserClient(feignClient);
    }

    @Test
    void createAUserShouldSuccessWhenResponseReturn204() {
        when(feignClient.createAUser(any(), any())).thenReturn(Response.builder()
            .status(204)
            .request(mock(Request.class))
            .build());
        assertThatCode(() -> testee.createAUser("username", "pass"))
            .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 409, 500})
    void createAUserShouldThrowWhenResponseReturnIsNot204(int responseCode) {
        when(feignClient.createAUser(any(), any())).thenReturn(Response.builder()
            .status(responseCode)
            .body("", StandardCharsets.UTF_8)
            .request(mock(Request.class))
            .build());
        assertThatThrownBy(() -> testee.createAUser("username", "pass"))
            .isInstanceOf(JamesFeignException.class);
    }

    @Test
    void updateAUserPasswordShouldSuccessWhenResponse204() {
        when(feignClient.updateAUserPassword(any(), any())).thenReturn(Response.builder()
            .status(204)
            .request(mock(Request.class))
            .build());
        assertThatCode(() -> testee.updateAUserPassword("username", "pass"))
            .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 409, 500})
    void updateAUserPasswordShouldThrowWhenResponseReturnIsNot204(int responseCode) {
        when(feignClient.updateAUserPassword(any(), any())).thenReturn(Response.builder()
            .status(responseCode)
            .body("", StandardCharsets.UTF_8)
            .request(mock(Request.class))
            .build());
        assertThatThrownBy(() -> testee.updateAUserPassword("username", "pass"))
            .isInstanceOf(JamesFeignException.class);
    }

    @Test
    void deleteAUserShouldSuccessWhenResponse204() {
        when(feignClient.deleteAUser(any())).thenReturn(Response.builder()
            .status(204)
            .request(mock(Request.class))
            .body("", StandardCharsets.UTF_8)
            .build());
        assertThatCode(() -> testee.deleteAUser("username"))
            .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 409, 500})
    void deleteAUserShouldThrowWhenResponseReturnIsNot204(int responseCode) {
        when(feignClient.deleteAUser(any())).thenReturn(Response.builder()
            .status(responseCode)
            .body("", StandardCharsets.UTF_8)
            .request(mock(Request.class))
            .build());
        assertThatThrownBy(() -> testee.deleteAUser("username"))
            .isInstanceOf(JamesFeignException.class);
    }

    @Test
    void doesExistShouldReturnTrueWhenResponse200() {
        when(feignClient.doesExist(any())).thenReturn(Response.builder()
            .status(200)
            .body("", StandardCharsets.UTF_8)
            .request(mock(Request.class))
            .build());

        assertThat(testee.doesExist("user"))
            .isTrue();
    }

    @Test
    void doesExistShouldReturnFalseWhenResponse404() {
        when(feignClient.doesExist(any())).thenReturn(Response.builder()
            .status(404)
            .body("", StandardCharsets.UTF_8)
            .request(mock(Request.class))
            .build());

        assertThat(testee.doesExist("user"))
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
        assertThatThrownBy(() -> testee.doesExist("username"))
            .isInstanceOf(JamesFeignException.class);
    }
}
