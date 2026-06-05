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

package org.apache.james.protocols.api.sasl;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class TestSaslSessionContext implements SaslSessionContext {
    private final Map<Class<?>, Object> services;

    TestSaslSessionContext(Optional<PasswordSaslAuthenticationService> passwordService,
                           Optional<BearerTokenSaslAuthenticationService> bearerTokenService) {
        this.services = new HashMap<>();
        passwordService.ifPresent(service -> register(PasswordSaslAuthenticationService.class, service));
        bearerTokenService.ifPresent(service -> register(BearerTokenSaslAuthenticationService.class, service));
    }

    @Override
    public <T> Optional<T> service(Class<T> serviceType) {
        return Optional.ofNullable(services.get(serviceType))
            .map(serviceType::cast);
    }

    @Override
    public <T> void register(Class<T> serviceType, T service) {
        services.put(serviceType, service);
    }
}
