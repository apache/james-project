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
import java.util.Set;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;

import com.google.common.collect.ImmutableSet;

/**
 * Handles USER command
 */
public class UserCmdHandler implements CommandHandler<POP3Session>, CapaCapability {

    private static final Collection<String> COMMANDS = ImmutableSet.of("USER");
    private static final Set<String> CAPS = ImmutableSet.of("USER");

    @Override
    public void init(Configuration config) throws ConfigurationException {

    }

    @Override
    public void destroy() {

    }

    /**
     * Handler method called upon receipt of a USER command. Reads in the user
     * id.
     */
    public Response onCommand(POP3Session session, Request request) {
        String parameters = request.getArgument();
        if (session.getHandlerState() == POP3Session.AUTHENTICATION_READY && parameters != null) {
            session.setUser(parameters);
            session.setHandlerState(POP3Session.AUTHENTICATION_USERSET);
            return POP3Response.OK;
        } else {
            return POP3Response.ERR;
        }
    }

    /**
     * @see org.apache.james.pop3server.core.CapaCapability#getImplementedCapabilities(org.apache.james.pop3server.POP3Session)
     */
    public Set<String> getImplementedCapabilities(POP3Session session) {
        return CAPS;
    }

    /**
     * @see org.apache.james.protocols.api.handler.CommandHandler#getImplCommands()
     */
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }
}
