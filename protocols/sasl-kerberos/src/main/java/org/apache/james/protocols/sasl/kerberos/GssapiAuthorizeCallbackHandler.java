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
import java.util.Locale;
import java.util.Optional;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.sasl.AuthorizeCallback;

import org.apache.james.core.Username;
import org.apache.james.protocols.api.sasl.SaslAuthenticationResult;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslFailure;
import org.apache.james.protocols.api.sasl.SaslIdentity;

class GssapiAuthorizeCallbackHandler implements CallbackHandler {
    private static Username canonicalAuthenticationId(String authenticationId) {
        if (authenticationId == null || authenticationId.chars().anyMatch(character -> character > 0x7F)) {
            throw new IllegalArgumentException("GSSAPI authentication identity must contain only ASCII characters");
        }

        KerberosPrincipal principal = new KerberosPrincipal(authenticationId);
        String realm = principal.getRealm();
        String principalName = principal.getName();
        String principalComponents = principalName.substring(0, principalName.length() - realm.length() - 1);
        String canonicalPrincipal = principalComponents.toLowerCase(Locale.ROOT) + "@" + realm.toUpperCase(Locale.ROOT);

        // James usernames are case-insensitive, so accept one Kerberos spelling to prevent case-distinct principals from collapsing.
        if (!authenticationId.equals(canonicalPrincipal)) {
            throw new IllegalArgumentException("GSSAPI authentication identity is not canonical");
        }

        Username username = Username.of(canonicalPrincipal);
        // Case folding is intentional; reject any additional normalization that could collapse distinct Kerberos principals.
        if (!username.asString().equals(canonicalPrincipal.toLowerCase(Locale.US))) {
            throw new IllegalArgumentException("GSSAPI authentication identity cannot be mapped without normalization");
        }
        return username;
    }

    private final SaslAuthenticator authenticator;
    private Optional<SaslAuthenticationResult> result;

    GssapiAuthorizeCallbackHandler(SaslAuthenticator authenticator) {
        this.authenticator = authenticator;
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
        try {
            Username authenticationId = canonicalAuthenticationId(callback.getAuthenticationID());
            Username authorizationId = Optional.ofNullable(callback.getAuthorizationID())
                .filter(value -> !value.isEmpty())
                .map(Username::of)
                .orElse(authenticationId);

            result = Optional.of(authenticator.authorize(new SaslIdentity(authenticationId, authorizationId)));
            switch (result.orElseThrow()) {
                case SaslAuthenticationResult.Success success -> {
                    callback.setAuthorized(true);
                    callback.setAuthorizedID(success.identity().authorizationId().asString());
                }
                case SaslAuthenticationResult.Failure ignored -> callback.setAuthorized(false);
            }
        } catch (IllegalArgumentException e) {
            result = Optional.of(new SaslAuthenticationResult.Failure(SaslFailure.malformed("Malformed GSSAPI identity.")));
            callback.setAuthorized(false);
        }
    }
}
