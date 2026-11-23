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

package org.apache.james.user.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.user.lib.model.Algorithm;
import org.apache.james.user.lib.model.DefaultUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemoryUsersRepositoryPasswordHashingTest {
    private static final Username USER = Username.of("user");
    private static final String PASSWORD = "Привет";

    private MemoryUsersRepository usersRepository;

    @BeforeEach
    void setUp() throws Exception {
        usersRepository = MemoryUsersRepository.withoutVirtualHosting(mock(DomainList.class));
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("algorithm", "SHA-512");
        usersRepository.configure(configuration);
    }

    @Test
    void aNonLatinPasswordShouldNotBeAcceptedForItsIsoTwin() throws Exception {
        usersRepository.addUser(USER, PASSWORD);

        assertThat(usersRepository.test(USER, "??????")).isEmpty();
        assertThat(usersRepository.test(USER, PASSWORD)).contains(USER);
    }

    @Test
    void anIsoDigestShouldStillBeVerifiedAndBeHashedAgainAfterTheLogin() throws Exception {
        String latinPassword = "Crème brûlée";
        usersRepository.addUser(USER, "any");
        Algorithm iso = Algorithm.of("SHA-512");
        usersRepository.updateUser(new DefaultUser(USER, iso.digest(latinPassword, USER.asString()), iso, iso));

        assertThat(usersRepository.test(USER, latinPassword)).contains(USER);

        DefaultUser rehashed = (DefaultUser) usersRepository.getUserByName(USER);
        assertThat(rehashed.getHashAlgorithm().asString()).isEqualTo("SHA-512-utf8/plain");
        assertThat(usersRepository.test(USER, latinPassword)).contains(USER);
    }

    @Test
    void anIsoDigestOfANonLatinPasswordShouldNotBeHashedAgainWithAMistypedPassword() throws Exception {
        usersRepository.addUser(USER, "any");
        Algorithm iso = Algorithm.of("SHA-512");
        usersRepository.updateUser(new DefaultUser(USER, iso.digest(PASSWORD, USER.asString()), iso, iso));

        // The ISO-8859-1 digest maps every Cyrillic letter to '?', it can not tell this typo from the real password
        usersRepository.test(USER, "Пришет");

        assertThat(usersRepository.test(USER, PASSWORD)).contains(USER);
    }

    @Test
    void anIsoDigestOfANonLatinPasswordShouldNotBeHashedAgainWithItsQuestionMarkTwin() throws Exception {
        usersRepository.addUser(USER, "any");
        Algorithm iso = Algorithm.of("SHA-512");
        usersRepository.updateUser(new DefaultUser(USER, iso.digest(PASSWORD, USER.asString()), iso, iso));

        usersRepository.test(USER, "??????");

        assertThat(usersRepository.test(USER, PASSWORD)).contains(USER);
    }
}
