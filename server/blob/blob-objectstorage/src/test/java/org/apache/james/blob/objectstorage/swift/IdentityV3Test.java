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

package org.apache.james.blob.objectstorage.swift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class IdentityV3Test {

    public static final DomainName DOMAIN_NAME = DomainName.of("domain");
    public static final UserName USER_NAME = UserName.of("user");

    @Test
    void identityV3RendersProperlyAsString() {
        IdentityV3 identity = IdentityV3.of(DOMAIN_NAME, USER_NAME);
        assertThat(identity.asString()).isEqualTo("domain:user");
    }

    @Test
    void identityV3ShouldRespectBeanContract() {
        EqualsVerifier.forClass(IdentityV3.class).verify();
    }


    @Test
    void userNameCanNotBeNull() {
        assertThatThrownBy(() -> IdentityV3.of(DOMAIN_NAME, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void domainNameCanNotBeNull() {
        assertThatThrownBy(() -> IdentityV3.of(null, USER_NAME)).isInstanceOf(IllegalArgumentException.class);
    }
}