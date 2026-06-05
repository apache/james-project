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

import java.util.Optional;

/**
 * Protocol-specific factory for services required by SASL mechanisms.
 */
public interface SaslAuthenticationServiceFactory<T> {
    /**
     * Protocol supported by the produced service.
     */
    SaslProtocol protocol();

    /**
     * Service type produced by this factory.
     */
    Class<T> serviceType();

    /**
     * Creates the service for the supplied SASL session context when available.
     */
    Optional<T> create(SaslSessionContext context);
}
