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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.api.handler.ExtensibleHandler;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.pop3.POP3Response;
import org.apache.james.protocols.pop3.POP3Session;
import org.apache.james.util.MDCBuilder;

import com.google.common.collect.ImmutableSet;

/**
 * This handler is used to handle CAPA commands
 */
public class CapaCmdHandler implements CommandHandler<POP3Session>, ExtensibleHandler, CapaCapability {    
    private List<CapaCapability> caps;
    private static final Collection<String> COMMANDS = ImmutableSet.of("CAPA");
    private static final Set<String> CAPS = ImmutableSet.of("PIPELINING");

    @Override
    public Response onCommand(POP3Session session, Request request) {
        return MDCBuilder.withMdc(MDCBuilder.create()
                .addContext(MDCBuilder.ACTION, "CAPA")
                .addContext(MDCConstants.withSession(session)),
            () -> capa(session));
    }

    private Response capa(POP3Session session) {
        POP3Response response = new POP3Response(POP3Response.OK_RESPONSE, "Capability list follows");

        for (CapaCapability capabilities : caps) {
            for (String cap : capabilities.getImplementedCapabilities(session)) {
                response.appendLine(cap);
            }
        }
        response.appendLine(".");
        return response;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public List<Class<?>> getMarkerInterfaces() {
        List<Class<?>> mList = new ArrayList();
        mList.add(CapaCapability.class);
        return mList;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void wireExtensions(Class<?> interfaceName, List<?> extension) throws WiringException {
        if (interfaceName.equals(CapaCapability.class)) {
            caps = (List<CapaCapability>) extension;
        }
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

    @Override
    public Set<String> getImplementedCapabilities(POP3Session session) {
        return CAPS;
    }

}
