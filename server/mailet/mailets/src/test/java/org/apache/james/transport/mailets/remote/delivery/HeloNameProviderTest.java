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

package org.apache.james.transport.mailets.remote.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HeloNameProviderTest {

    public static final String DOMAIN = "domain";

    private DomainList domainList;

    @BeforeEach
    void setUp() {
        domainList = mock(DomainList.class);
    }

    @Test
    void getHeloNameShouldReturnNonNullProvidedHeloName() {
        HeloNameProvider heloNameProvider = new HeloNameProvider(DOMAIN, domainList);

        assertThat(heloNameProvider.getHeloName()).isEqualTo(DOMAIN);
    }

    @Test
    void getHeloNameShouldReturnDomainListDefaultDomainOnNullHeloName() throws DomainListException {
        when(domainList.getDefaultDomain()).thenReturn(Domain.of(DOMAIN));
        HeloNameProvider heloNameProvider = new HeloNameProvider(null, domainList);

        assertThat(heloNameProvider.getHeloName()).isEqualTo(DOMAIN);
    }

    @Test
    void getHeloNameShouldReturnLocalhostOnDomainListException() throws DomainListException {
        when(domainList.getDefaultDomain()).thenThrow(new DomainListException("any message"));
        HeloNameProvider heloNameProvider = new HeloNameProvider(null, domainList);

        assertThat(heloNameProvider.getHeloName()).isEqualTo(HeloNameProvider.LOCALHOST);
    }

    @Test
    void getHeloNameShouldPropagateRuntimeExceptions() throws DomainListException {
        when(domainList.getDefaultDomain()).thenThrow(new RuntimeException());
        HeloNameProvider heloNameProvider = new HeloNameProvider(null, domainList);

        assertThatThrownBy(heloNameProvider::getHeloName)
            .isInstanceOf(RuntimeException.class);
    }

}
