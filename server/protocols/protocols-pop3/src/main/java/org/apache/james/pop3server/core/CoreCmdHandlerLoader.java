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

package org.apache.james.pop3server.core;

import java.util.LinkedList;
import java.util.List;

import org.apache.james.protocols.api.handler.CommandDispatcher;
import org.apache.james.protocols.api.handler.CommandHandlerResultLogger;
import org.apache.james.protocols.lib.handler.HandlersPackage;
import org.apache.james.protocols.pop3.core.CapaCmdHandler;
import org.apache.james.protocols.pop3.core.DeleCmdHandler;
import org.apache.james.protocols.pop3.core.ListCmdHandler;
import org.apache.james.protocols.pop3.core.NoopCmdHandler;
import org.apache.james.protocols.pop3.core.QuitCmdHandler;
import org.apache.james.protocols.pop3.core.RetrCmdHandler;
import org.apache.james.protocols.pop3.core.RsetCmdHandler;
import org.apache.james.protocols.pop3.core.StatCmdHandler;
import org.apache.james.protocols.pop3.core.StlsCmdHandler;
import org.apache.james.protocols.pop3.core.TopCmdHandler;
import org.apache.james.protocols.pop3.core.UidlCmdHandler;
import org.apache.james.protocols.pop3.core.UnknownCmdHandler;
import org.apache.james.protocols.pop3.core.UserCmdHandler;
import org.apache.james.protocols.pop3.core.WelcomeMessageHandler;

public class CoreCmdHandlerLoader implements HandlersPackage {


    private static final List<String> commands = List.of(
            // Insert the base commands in the Map
            WelcomeMessageHandler.class.getName(),
            CommandDispatcher.class.getName(),
            CapaCmdHandler.class.getName(),
            UserCmdHandler.class.getName(),
            PassCmdHandler.class.getName(),
            ListCmdHandler.class.getName(),
            UidlCmdHandler.class.getName(),
            RsetCmdHandler.class.getName(),
            DeleCmdHandler.class.getName(),
            NoopCmdHandler.class.getName(),
            RetrCmdHandler.class.getName(),
            TopCmdHandler.class.getName(),
            StatCmdHandler.class.getName(),
            QuitCmdHandler.class.getName(),
            UnknownCmdHandler.class.getName(),
            // add STARTTLS support to the core. See JAMES-1224
            StlsCmdHandler.class.getName(),
            // Add logging stuff
            CommandHandlerResultLogger.class.getName()
    );

    public CoreCmdHandlerLoader() {}

    @Override
    public List<String> getHandlers() {
        return commands;
    }
}
