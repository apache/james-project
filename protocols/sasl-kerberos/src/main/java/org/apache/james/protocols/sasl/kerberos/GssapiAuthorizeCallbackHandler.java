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

package org.apache.james.protocols.sasl.kerberos;

import java.io.IOException;
import java.util.Optional;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;

import org.apache.james.core.Username;
import org.apache.james.protocols.api.sasl.SaslAuthenticationResult;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslFailure;
import org.apache.james.protocols.api.sasl.SaslIdentity;

class GssapiAuthorizeCallbackHandler implements CallbackHandler {
    private static class UnresolvableIdentity extends RuntimeException {
        private final SaslFailure failure;

        UnresolvableIdentity(SaslFailure failure) {
            super(failure.reason(), null, false, false);
            this.failure = failure;
        }

        SaslFailure failure() {
            return failure;
        }
    }

    private static final SaslFailure MALFORMED = SaslFailure.malformed("Malformed GSSAPI identity.");

    private final SaslAuthenticator authenticator;
    private final RealmMapping realmMapping;
    private Optional<SaslAuthenticationResult> result;

    GssapiAuthorizeCallbackHandler(SaslAuthenticator authenticator, RealmMapping realmMapping) {
        this.authenticator = authenticator;
        this.realmMapping = realmMapping;
        this.result = Optional.empty();
    }

    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        for (Callback callback : callbacks) {
            if (!(callback instanceof AuthorizeCallback authorizeCallback)) {
                throw new UnsupportedCallbackException(callback);
            }
            authorize(authorizeCallback);
        }
    }

    Optional<SaslAuthenticationResult> result() {
        return result;
    }

    private void authorize(AuthorizeCallback callback) {
        SaslIdentity identity;
        try {
            Username authenticationId = resolve(canonicalPrincipal(callback.getAuthenticationID()));
            identity = new SaslIdentity(authenticationId, Optional.ofNullable(callback.getAuthorizationID())
                .filter(value -> !value.isEmpty())
                .map(this::authorizationId)
                .orElse(authenticationId));
        } catch (UnresolvableIdentity e) {
            result = Optional.of(new SaslAuthenticationResult.Failure(e.failure()));
            callback.setAuthorized(false);
            return;
        }

        result = Optional.of(authenticator.authorize(identity));
        switch (result.orElseThrow()) {
            case SaslAuthenticationResult.Success success -> {
                callback.setAuthorized(true);
                callback.setAuthorizedID(success.identity().authorizationId().asString());
            }
            case SaslAuthenticationResult.Failure ignored -> callback.setAuthorized(false);
        }
    }

    /**
     * GSSAPI implementations echo the client principal when the client requests no specific authorization identity, so
     * a canonically spelled Kerberos principal goes through the realm mapping like the authentication identity. Anything
     * else is the James username the client asks to act as, and the authenticator applies the usual delegation rules.
     */
    private Username authorizationId(String authorizationId) {
        Optional<CanonicalKerberosPrincipal> principal = principal(authorizationId);
        if (principal.isPresent()) {
            return resolve(principal.get());
        }
        try {
            return Username.of(authorizationId);
        } catch (IllegalArgumentException e) {
            throw new UnresolvableIdentity(MALFORMED);
        }
    }

    private CanonicalKerberosPrincipal canonicalPrincipal(String authenticationId) {
        return principal(authenticationId).orElseThrow(() -> new UnresolvableIdentity(MALFORMED));
    }

    private Optional<CanonicalKerberosPrincipal> principal(String value) {
        try {
            return Optional.of(CanonicalKerberosPrincipal.parse(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Username resolve(CanonicalKerberosPrincipal principal) {
        Optional<Username> username;
        try {
            username = realmMapping.resolve(principal);
        } catch (IllegalArgumentException e) {
            throw new UnresolvableIdentity(MALFORMED);
        }
        return username.orElseThrow(() -> new UnresolvableIdentity(SaslFailure.authenticationFailed(
            Optional.empty(), Optional.empty(), "GSSAPI principal is not mapped to a James identity.")));
    }
}
