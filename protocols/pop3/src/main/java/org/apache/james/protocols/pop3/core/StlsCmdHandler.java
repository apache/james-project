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

package org.apache.james.protocols.pop3.core;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.protocols.pop3.POP3StartTlsResponse;

import com.google.common.collect.ImmutableSet;

/**
 * Handler which offer STARTTLS implementation for POP3. STARTTLS is started
 * with the STSL command
 */
public class StlsCmdHandler implements CommandHandler<POP3Session>, CapaCapability {

    private static final Collection<String> COMMANDS = ImmutableSet.of("STLS");
    private static final Set<String> CAPS = ImmutableSet.of("STLS");

    private static final Response BEGIN_TLS = new POP3StartTlsResponse(POP3Response.OK_RESPONSE, "Begin TLS negotiation").immutable();

    @Override
    public void init(Configuration config) throws ConfigurationException {

    }

    @Override
    public void destroy() {

    }

    @Override
    public Response onCommand(POP3Session session, Request request) {
        // check if starttls is supported, the state is the right one and it was
        // not started before
        if (session.isStartTLSSupported() && session.getHandlerState() == POP3Session.AUTHENTICATION_READY && session.isTLSStarted() == false) {
            return BEGIN_TLS;
        } else {
            return POP3Response.ERR;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getImplementedCapabilities(POP3Session session) {
        if (session.isStartTLSSupported() && session.getHandlerState() == POP3Session.AUTHENTICATION_READY) {
            return CAPS;
        } else {
            return Collections.EMPTY_SET;
        }
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }
}
