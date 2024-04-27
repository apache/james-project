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

package org.apache.james.backends.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class JPAConfigurationTest {

    private static final String DRIVER_NAME = "driverName";
    private static final String DRIVER_URL = "driverUrl";
    private static final boolean TEST_ON_BORROW = true;
    private static final String VALIDATION_QUERY = "validationQuery";
    private static final int VALIDATION_TIMEOUT_SEC = 1;
    private static final int MAX_CONNECTIONS = 5;
    private static final String USER_NAME = "username";
    private static final String PASSWORD = "password";
    private static final String EMPTY_STRING = "";
    private static final boolean ATTACHMENT_STORAGE = true;

    @Test
    void buildShouldReturnCorrespondingProperties() {
        JPAConfiguration configuration = JPAConfiguration.builder()
            .driverName(DRIVER_NAME)
            .driverURL(DRIVER_URL)
            .testOnBorrow(TEST_ON_BORROW)
            .validationQuery(VALIDATION_QUERY)
            .validationQueryTimeoutSec(VALIDATION_TIMEOUT_SEC)
            .username(USER_NAME)
            .password(PASSWORD)
            .maxConnections(MAX_CONNECTIONS)
            .attachmentStorage(ATTACHMENT_STORAGE)
            .build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(configuration.getDriverName()).isEqualTo(DRIVER_NAME);
            softly.assertThat(configuration.getDriverURL()).isEqualTo(DRIVER_URL);
            softly.assertThat(configuration.isTestOnBorrow()).contains(TEST_ON_BORROW);
            softly.assertThat(configuration.getValidationQuery()).contains(VALIDATION_QUERY);
            softly.assertThat(configuration.getValidationQueryTimeoutSec()).contains(VALIDATION_TIMEOUT_SEC);
            softly.assertThat(configuration.getCredential()).hasValueSatisfying(credential -> {
                softly.assertThat(credential.getPassword()).isEqualTo(PASSWORD);
                softly.assertThat(credential.getUsername()).isEqualTo(USER_NAME);
            });
            softly.assertThat(configuration.getMaxConnections()).contains(MAX_CONNECTIONS);
            softly.assertThat(configuration.isAttachmentStorageEnabled()).contains(ATTACHMENT_STORAGE);
        });
    }

    @Test
    void buildShouldReturnEmptyOptionalPropertiesWhenNotSpecified() {
        JPAConfiguration configuration = JPAConfiguration.builder()
            .driverName(DRIVER_NAME)
            .driverURL(DRIVER_URL)
            .build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(configuration.getDriverName()).isEqualTo(DRIVER_NAME);
            softly.assertThat(configuration.getDriverURL()).isEqualTo(DRIVER_URL);

            softly.assertThat(configuration.isTestOnBorrow()).isEmpty();
            softly.assertThat(configuration.getValidationQuery()).isEmpty();
            softly.assertThat(configuration.getValidationQueryTimeoutSec()).isEmpty();
            softly.assertThat(configuration.getCredential()).isEmpty();
            softly.assertThat(configuration.getMaxConnections()).isEmpty();
            softly.assertThat(configuration.isAttachmentStorageEnabled()).isEmpty();
        });
    }

    @Test
    void buildShouldThrowWhenValidationQueryTimeoutSecIsZero() {
        assertThatThrownBy(() -> JPAConfiguration.builder()
            .driverName(DRIVER_NAME)
            .driverURL(DRIVER_URL)
            .validationQueryTimeoutSec(0)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("validationQueryTimeoutSec is required to be greater than 0");
    }

    @Test
    void buildShouldThrowWhenMaxConnectionIsZero() {
        assertThatThrownBy(() -> JPAConfiguration.builder()
            .driverName(DRIVER_NAME)
            .driverURL(DRIVER_URL)
            .maxConnections(0)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("maxConnections is required to be -1 (no limit) or  greater than 0");
    }

    @Test
    void buildShouldThrowWhenMaxConnectionIsLesThanMinusOne() {
        assertThatThrownBy(() -> JPAConfiguration.builder()
            .driverName(DRIVER_NAME)
            .driverURL(DRIVER_URL)
            .maxConnections(-10)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("maxConnections is required to be -1 (no limit) or  greater than 0");
    }

    @Test
    void buildShouldBuildWhenMaxConnectionIsMinusOne() {
        JPAConfiguration configuration = JPAConfiguration.builder()
            .driverName(DRIVER_NAME)
            .driverURL(DRIVER_URL)
            .maxConnections(-1)
            .build();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(configuration.getDriverName()).isEqualTo(DRIVER_NAME);
            softly.assertThat(configuration.getDriverURL()).isEqualTo(DRIVER_URL);

            softly.assertThat(configuration.isTestOnBorrow()).isEmpty();
            softly.assertThat(configuration.getValidationQuery()).isEmpty();
            softly.assertThat(configuration.getValidationQueryTimeoutSec()).isEmpty();
            softly.assertThat(configuration.getCredential()).isEmpty();
            softly.assertThat(configuration.getMaxConnections()).contains(-1);
        });
    }

    @Test
    void buildShouldThrowWhenValidationQueryTimeoutSecIsNegative() {
        assertThatThrownBy(() -> JPAConfiguration.builder()
            .driverName(DRIVER_NAME)
            .driverURL(DRIVER_URL)
            .validationQueryTimeoutSec(-1)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("validationQueryTimeoutSec is required to be greater than 0");
    }

    @Test
    void buildShouldThrowWhenPasswordIsNull() {
        assertThatThrownBy(() -> JPAConfiguration.builder()
            .driverName(DRIVER_NAME)
            .driverURL(DRIVER_URL)
            .username(USER_NAME)
            .password(null)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("username and password for connecting to database can't be blank");
    }

    @Test
    void buildShouldThrowWhenPasswordIsEmpty() {
        assertThatThrownBy(() -> JPAConfiguration.builder()
            .driverName(DRIVER_NAME)
            .driverURL(DRIVER_URL)
            .username(USER_NAME)
            .password(EMPTY_STRING)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("username and password for connecting to database can't be blank");
    }

    @Test
    void buildShouldThrowWhenUsernameIsNull() {
        assertThatThrownBy(() -> JPAConfiguration.builder()
            .driverName(DRIVER_NAME)
            .driverURL(DRIVER_URL)
            .username(null)
            .password(PASSWORD)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("username and password for connecting to database can't be blank");
    }

    @Test
    void buildShouldThrowWhenUsernameIsEmpty() {
        assertThatThrownBy(() -> JPAConfiguration.builder()
            .driverName(DRIVER_NAME)
            .driverURL(DRIVER_URL)
            .username(EMPTY_STRING)
            .password(PASSWORD)
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("username and password for connecting to database can't be blank");
    }

    @Test
    void buildShouldReturnEmptyOptionalCredentialWhenPassingNullValues() {
        JPAConfiguration configuration = JPAConfiguration.builder()
            .driverName(DRIVER_NAME)
            .driverURL(DRIVER_URL)
            .username(null)
            .password(null)
            .build();

        assertThat(configuration.getCredential()).isEmpty();
    }
}