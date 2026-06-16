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

import org.apache.james.core.Username;

/**
 * Protocol-neutral authentication service available to SASL mechanisms.
 */
public interface SaslAuthenticator {
    /**
     * Verifies password credentials and, when present, validates that the authenticated user may act as the
     * requested authorization identity.
     */
    SaslAuthenticationResult authenticatePassword(Username authenticationId,
                                                  Optional<Username> authorizationId,
                                                  String password);

    /**
     * Validates an already-authenticated identity, typically for token or Kerberos mechanisms that verified
     * credentials inside the SASL exchange.
     */
    SaslAuthenticationResult authorize(SaslIdentity identity);
}
