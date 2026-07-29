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

package org.apache.james.imap.api.process;

import org.apache.james.protocols.api.sasl.SaslExchange;

/**
 * Owns the active SASL exchange for an IMAP session. Once closed on disconnect,
 * the tracker stays attached and rejects delayed asynchronous registrations.
 */
public class ImapSaslExchangeTracker {
    private static final String ATTRIBUTE_KEY = ImapSaslExchangeTracker.class.getName();

    public static ImapSaslExchangeTracker forSession(ImapSession session) {
        // Make tracker initialization atomic with disconnect sealing for this session.
        synchronized (session) {
            Object value = session.getAttribute(ATTRIBUTE_KEY);
            if (value instanceof ImapSaslExchangeTracker tracker) {
                return tracker;
            }

            ImapSaslExchangeTracker tracker = new ImapSaslExchangeTracker();
            session.setAttribute(ATTRIBUTE_KEY, tracker);
            return tracker;
        }
    }

    public static void closeForSession(ImapSession session) {
        forSession(session).close();
    }

    private static IllegalStateException closeRejectedExchange(SaslExchange exchange) {
        IllegalStateException failure = new IllegalStateException("IMAP SASL exchange cannot be registered");
        try {
            exchange.close();
        } catch (RuntimeException e) {
            failure.addSuppressed(e);
        }
        return failure;
    }

    private SaslExchange activeExchange;
    private boolean closed;

    private ImapSaslExchangeTracker() {
    }

    public SaslExchange register(SaslExchange exchange) {
        if (tryRegister(exchange)) {
            return exchange;
        }
        throw closeRejectedExchange(exchange);
    }

    private synchronized boolean tryRegister(SaslExchange exchange) {
        if (closed || activeExchange != null) {
            return false;
        }
        activeExchange = exchange;
        return true;
    }

    public void closeExchange(SaslExchange exchange) {
        if (release(exchange)) {
            exchange.close();
        }
    }

    public void abortExchange(SaslExchange exchange) {
        if (release(exchange)) {
            exchange.abort();
        }
    }

    public void close() {
        SaslExchange exchange;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            exchange = activeExchange;
            activeExchange = null;
        }

        if (exchange != null) {
            exchange.close();
        }
    }

    private synchronized boolean release(SaslExchange exchange) {
        if (activeExchange != exchange) {
            return false;
        }
        activeExchange = null;
        return true;
    }
}
