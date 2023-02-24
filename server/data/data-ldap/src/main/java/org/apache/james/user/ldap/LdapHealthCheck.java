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
package org.apache.james.user.ldap;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.core.healthcheck.ComponentName;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.core.healthcheck.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class LdapHealthCheck implements HealthCheck {
    public static final ComponentName COMPONENT_NAME = new ComponentName("LDAP User Server");
    public static final Username LDAP_TEST_USER = Username.of("ldap-test");
    private static final Logger LOGGER = LoggerFactory.getLogger(LdapHealthCheck.class);

    private final ReadOnlyUsersLDAPRepository ldapUserRepository;

    @Inject
    public LdapHealthCheck(ReadOnlyUsersLDAPRepository ldapUserRepository) {
        this.ldapUserRepository = ldapUserRepository;
    }

    @Override
    public ComponentName componentName() {
        return COMPONENT_NAME;
    }

    @Override
    public Mono<Result> check() {
        return Mono.fromCallable(() -> ldapUserRepository.getUserByName(LDAP_TEST_USER))
            .thenReturn(Result.healthy(COMPONENT_NAME))
            .doOnError(e -> LOGGER.error("Error in LDAP server", e))
            .onErrorResume(e -> Mono.just(Result.unhealthy(COMPONENT_NAME, "Error checking LDAP server!", e)))
            .subscribeOn(Schedulers.boundedElastic());
    }
}
