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

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;

import javax.security.auth.Subject;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

public class GssapiTestClient implements AutoCloseable {
    private static final String KRB5_LOGIN_MODULE = "com.sun.security.auth.module.Krb5LoginModule";
    private static final String LOGIN_CONTEXT_NAME = "JamesGssapiTestClient";

    private final LoginContext loginContext;
    private final Subject subject;
    private final SaslClient saslClient;

    GssapiTestClient(String principal,
                     Path keyTab,
                     String serviceName,
                     String serverName,
                     Optional<String> authorizationId) throws LoginException, SaslException {
        LoginContext context = new LoginContext(LOGIN_CONTEXT_NAME, null, null, jaasConfiguration(principal, keyTab));
        context.login();
        Subject authenticatedSubject = context.getSubject();
        try {
            SaslClient client = call(authenticatedSubject, () -> Sasl.createSaslClient(
                new String[] { "GSSAPI" },
                authorizationId.orElse(null),
                serviceName,
                serverName,
                Map.of(Sasl.QOP, "auth", Sasl.SERVER_AUTH, "true"),
                null));
            if (client == null) {
                throw new SaslException("No JDK GSSAPI SASL client provider is available");
            }
            loginContext = context;
            subject = authenticatedSubject;
            saslClient = client;
        } catch (RuntimeException | SaslException e) {
            context.logout();
            throw e;
        }
    }

    public byte[] initialResponse() throws SaslException {
        if (!saslClient.hasInitialResponse()) {
            throw new SaslException("JDK GSSAPI SASL client did not provide an initial response");
        }
        return evaluate(new byte[0]);
    }

    public byte[] evaluate(byte[] challenge) throws SaslException {
        byte[] response = call(() -> saslClient.evaluateChallenge(challenge));
        return response == null ? new byte[0] : response;
    }

    public boolean isComplete() {
        return saslClient.isComplete();
    }

    @Override
    public void close() throws Exception {
        try {
            call(() -> {
                saslClient.dispose();
                return null;
            });
        } finally {
            loginContext.logout();
        }
    }

    private static Configuration jaasConfiguration(String principal, Path keyTab) {
        Map<String, String> options = Map.of(
            "doNotPrompt", "true",
            "isInitiator", "true",
            "keyTab", keyTab.toAbsolutePath().toString(),
            "principal", principal,
            "refreshKrb5Config", "true",
            "storeKey", "true",
            "useKeyTab", "true",
            "useTicketCache", "false");

        return new Configuration() {
            @Override
            public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
                return new AppConfigurationEntry[] {
                    new AppConfigurationEntry(KRB5_LOGIN_MODULE, AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options)
                };
            }
        };
    }

    private <T> T call(Callable<T> action) throws SaslException {
        return call(subject, action);
    }

    private static <T> T call(Subject subject, Callable<T> action) throws SaslException {
        try {
            return Subject.callAs(subject, action);
        } catch (CompletionException e) {
            if (e.getCause() instanceof SaslException saslException) {
                throw saslException;
            }
            throw e;
        }
    }
}
