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
 * Protocol-neutral SASL failure with enough metadata for protocol-specific response mapping and audit logging.
 */
public record SaslFailure(Type type,
                          Optional<Username> authenticationId,
                          Optional<Username> authorizationId,
                          String reason,
                          Optional<Throwable> cause) {
    public enum Type {
        MALFORMED,
        INVALID_CREDENTIALS,
        AUTHENTICATION_FAILED,
        USER_DOES_NOT_EXIST,
        DELEGATION_FORBIDDEN,
        SERVER_ERROR
    }

    public static SaslFailure malformed(String reason) {
        return new SaslFailure(Type.MALFORMED, Optional.empty(), Optional.empty(), reason, Optional.empty());
    }

    public static SaslFailure invalidCredentials(Username authenticationId, Optional<Username> authorizationId, String reason) {
        return new SaslFailure(Type.INVALID_CREDENTIALS, Optional.of(authenticationId), authorizationId, reason, Optional.empty());
    }

    public static SaslFailure authenticationFailed(Optional<Username> authenticationId, Optional<Username> authorizationId, String reason) {
        return new SaslFailure(Type.AUTHENTICATION_FAILED, authenticationId, authorizationId, reason, Optional.empty());
    }

    public static SaslFailure userDoesNotExist(Username authenticationId, Username authorizationId, String reason) {
        return new SaslFailure(Type.USER_DOES_NOT_EXIST, Optional.of(authenticationId), Optional.of(authorizationId), reason, Optional.empty());
    }

    public static SaslFailure delegationForbidden(Username authenticationId, Username authorizationId, String reason) {
        return new SaslFailure(Type.DELEGATION_FORBIDDEN, Optional.of(authenticationId), Optional.of(authorizationId), reason, Optional.empty());
    }

    public static SaslFailure serverError(Optional<Username> authenticationId, Optional<Username> authorizationId, String reason, Throwable cause) {
        return new SaslFailure(Type.SERVER_ERROR, authenticationId, authorizationId, reason, Optional.ofNullable(cause));
    }
}
