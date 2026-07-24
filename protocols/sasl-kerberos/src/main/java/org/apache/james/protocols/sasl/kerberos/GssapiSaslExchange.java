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

import java.util.Optional;

import javax.security.auth.login.LoginException;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.james.protocols.api.sasl.SaslAuthenticationResult;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslFailure;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class GssapiSaslExchange implements SaslExchange {
    private enum State {
        NEW,
        ACTIVE,
        CLOSED
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(GssapiSaslExchange.class);

    private final SaslInitialRequest request;
    private final GssapiSaslConfiguration configuration;
    private final KerberosLoginContextFactory loginContextFactory;
    private final GssapiSaslServerFactory saslServerFactory;
    private final GssapiAuthorizeCallbackHandler callbackHandler;

    private State state;
    private KerberosLoginContext loginContext;
    private SaslServer saslServer;

    GssapiSaslExchange(SaslInitialRequest request,
                       SaslAuthenticator authenticator,
                       GssapiSaslConfiguration configuration,
                       KerberosLoginContextFactory loginContextFactory,
                       GssapiSaslServerFactory saslServerFactory) {
        this.request = request;
        this.configuration = configuration;
        this.loginContextFactory = loginContextFactory;
        this.saslServerFactory = saslServerFactory;
        this.callbackHandler = new GssapiAuthorizeCallbackHandler(authenticator);
        this.state = State.NEW;
    }

    @Override
    public synchronized SaslStep firstStep() {
        if (state != State.NEW) {
            return terminalFailure(SaslFailure.malformed("GSSAPI exchange was already started."));
        }
        state = State.ACTIVE;
        return request.initialResponse()
            .map(this::evaluate)
            .orElseGet(() -> new SaslStep.Challenge(Optional.empty()));
    }

    @Override
    public synchronized SaslStep onResponse(byte[] clientResponse) {
        if (state != State.ACTIVE) {
            return terminalFailure(SaslFailure.malformed("GSSAPI exchange is not active."));
        }
        return evaluate(clientResponse);
    }

    @Override
    public synchronized void close() {
        if (state != State.CLOSED) {
            state = State.CLOSED;
            cleanup();
        }
    }

    private SaslStep evaluate(byte[] clientResponse) {
        try {
            initialize();
        } catch (LoginException | SaslException e) {
            return terminalFailure(SaslFailure.serverError(Optional.empty(), Optional.empty(), "GSSAPI server credentials are unavailable.", e));
        } catch (RuntimeException e) {
            return terminalFailure(SaslFailure.serverError(Optional.empty(), Optional.empty(), "GSSAPI authentication initialization failed.", e));
        }

        try {
            byte[] output = SubjectSaslServer.evaluate(loginContext.subject(), saslServer, clientResponse);
            if (!saslServer.isComplete()) {
                return new SaslStep.Challenge(optional(output));
            }
            return complete(output);
        } catch (SaslException e) {
            return callbackFailure()
                .map(this::terminalFailure)
                .orElseGet(() -> terminalFailure(SaslFailure.authenticationFailed(Optional.empty(), Optional.empty(), "GSSAPI authentication failed.")));
        } catch (RuntimeException e) {
            return terminalFailure(SaslFailure.serverError(Optional.empty(), Optional.empty(), "GSSAPI authentication failed.", e));
        }
    }

    private void initialize() throws LoginException, SaslException {
        if (saslServer == null) {
            KerberosLoginContext newLoginContext = loginContextFactory.login(configuration);
            try {
                SaslServer newSaslServer = SubjectSaslServer.create(newLoginContext.subject(), saslServerFactory, configuration, callbackHandler);
                loginContext = newLoginContext;
                saslServer = newSaslServer;
            } catch (SaslException | RuntimeException e) {
                closeAfterInitializationFailure(newLoginContext, e);
                throw e;
            }
        }
    }

    private void closeAfterInitializationFailure(KerberosLoginContext newLoginContext, Exception failure) {
        try {
            newLoginContext.close();
        } catch (LoginException e) {
            failure.addSuppressed(e);
        }
    }

    private SaslStep complete(byte[] output) {
        if (output != null && output.length > 0) {
            return terminalFailure(SaslFailure.serverError(Optional.empty(), Optional.empty(), "GSSAPI provider returned unexpected final server data."));
        }
        if (!"auth".equals(saslServer.getNegotiatedProperty(Sasl.QOP))) {
            return terminalFailure(SaslFailure.serverError(Optional.empty(), Optional.empty(), "GSSAPI negotiated an unsupported security layer."));
        }

        return callbackHandler.result()
            .map(this::completeAuthorization)
            .orElseGet(() -> terminalFailure(SaslFailure.serverError(Optional.empty(), Optional.empty(), "GSSAPI authorization did not complete.")));
    }

    private SaslStep completeAuthorization(SaslAuthenticationResult result) {
        return switch (result) {
            case SaslAuthenticationResult.Success success -> terminalSuccess(new SaslStep.Success(success.identity(), Optional.empty()));
            case SaslAuthenticationResult.Failure failure -> terminalFailure(failure.failure());
        };
    }

    private Optional<SaslFailure> callbackFailure() {
        return callbackHandler.result()
            .filter(SaslAuthenticationResult.Failure.class::isInstance)
            .map(SaslAuthenticationResult.Failure.class::cast)
            .map(SaslAuthenticationResult.Failure::failure);
    }

    private SaslStep terminalSuccess(SaslStep.Success success) {
        close();
        return success;
    }

    private SaslStep terminalFailure(SaslFailure failure) {
        close();
        return new SaslStep.Failure(failure);
    }

    private Optional<byte[]> optional(byte[] value) {
        if (value == null || value.length == 0) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private void cleanup() {
        if (saslServer != null && loginContext != null) {
            try {
                SubjectSaslServer.dispose(loginContext.subject(), saslServer);
            } catch (SaslException e) {
                LOGGER.warn("Failed to dispose GSSAPI SASL server", e);
            } finally {
                saslServer = null;
            }
        }
        if (loginContext != null) {
            try {
                loginContext.close();
            } catch (LoginException e) {
                LOGGER.warn("Failed to logout GSSAPI acceptor", e);
            } finally {
                loginContext = null;
            }
        }
    }
}
