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

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

final class SubjectSaslServer {
    static SaslServer create(Subject subject,
                             GssapiSaslServerFactory saslServerFactory,
                             GssapiSaslConfiguration configuration,
                             CallbackHandler callbackHandler) throws SaslException {
        return callAs(subject, () -> saslServerFactory.create(configuration, callbackHandler));
    }

    static byte[] evaluate(Subject subject, SaslServer saslServer, byte[] response) throws SaslException {
        return callAs(subject, () -> saslServer.evaluateResponse(response));
    }

    static void dispose(Subject subject, SaslServer saslServer) throws SaslException {
        callAs(subject, () -> {
            saslServer.dispose();
            return null;
        });
    }

    private static <T> T callAs(Subject subject, Callable<T> action) throws SaslException {
        try {
            return Subject.callAs(subject, action);
        } catch (CompletionException e) {
            if (e.getCause() instanceof SaslException saslException) {
                throw saslException;
            }
            throw new SaslException("GSSAPI operation failed", e.getCause());
        }
    }

    private SubjectSaslServer() {
    }
}
