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
package org.apache.james.domainlist.lib;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Fail.fail;

import org.apache.james.domainlist.api.DomainListException;
import org.junit.jupiter.api.Test;

public interface DomainListStrictContract extends DomainListIdempotentContract {
    @Test
    default void addDomainShouldThrowIfWeAddTwoTimesTheSameDomain() {
        try {
            domainList().addDomain(DOMAIN_1);
        } catch (Exception e) {
            fail(e.getMessage());
        }
        assertThatThrownBy(() -> domainList().addDomain(DOMAIN_1))
            .isInstanceOf(DomainListException.class);
    }

    @Test
    default void removeDomainShouldThrowIfTheDomainIsAbsent() {
        assertThatThrownBy(() -> domainList().removeDomain(DOMAIN_1))
            .isInstanceOf(DomainListException.class);
    }
}
