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
package org.apache.james.pop3server.jmx;

import java.util.Collection;

import org.apache.james.protocols.api.handler.CommandHandler;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.lib.jmx.AbstractCommandHandlerResultJMXMonitor;
import org.apache.james.protocols.lib.jmx.AbstractCommandHandlerStats;
import org.apache.james.protocols.pop3.POP3Session;

/**
 * Gather JMX stats for {@link CommandHandler}
 */
public class CommandHandlerResultJMXMonitor extends AbstractCommandHandlerResultJMXMonitor<POP3Session> implements ProtocolHandler {

    @Override
    protected AbstractCommandHandlerStats createCommandHandlerStats(CommandHandler<POP3Session> handler) throws Exception {
        Collection<String> col = handler.getImplCommands();
        String cName = handler.getClass().getName();

        return new POP3CommandHandlerStats(getJMXName(), cName, col.toArray(String[]::new));
    }

    @Override
    protected String getDefaultJMXName() {
        return "pop3server";
    }
}
