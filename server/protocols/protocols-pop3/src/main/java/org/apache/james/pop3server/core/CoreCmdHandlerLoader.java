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

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
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

    private final static String CAPACMDHANDLER = CapaCmdHandler.class.getName();
    private final static String USERCMDHANDLER = UserCmdHandler.class.getName();
    private final static String PASSCMDHANDLER = PassCmdHandler.class.getName();
    private final static String LISTCMDHANDLER = ListCmdHandler.class.getName();
    private final static String UIDLCMDHANDLER = UidlCmdHandler.class.getName();
    private final static String RSETCMDHANDLER = RsetCmdHandler.class.getName();
    private final static String DELECMDHANDLER = DeleCmdHandler.class.getName();
    private final static String NOOPCMDHANDLER = NoopCmdHandler.class.getName();
    private final static String RETRSCMDHANDLER = RetrCmdHandler.class.getName();
    private final static String TOPCMDHANDLER = TopCmdHandler.class.getName();
    private final static String STATCMDHANDLER = StatCmdHandler.class.getName();
    private final static String QUITCMDHANDLER = QuitCmdHandler.class.getName();
    private final static String WELCOMEMESSAGEHANDLER = WelcomeMessageHandler.class.getName();
    private final static String UNKOWNCMDHANDLER = UnknownCmdHandler.class.getName();
    private final static String STLSCMDHANDLER = StlsCmdHandler.class.getName();

    private final static String COMMANDDISPATCHER = CommandDispatcher.class.getName();

    // logging stuff
    private final static String COMMANDHANDLERRESULTLOGGER = CommandHandlerResultLogger.class.getName();


    private final List<String> commands = new LinkedList<>();

    public CoreCmdHandlerLoader() {
        // Insert the base commands in the Map
        commands.add(WELCOMEMESSAGEHANDLER);
        commands.add(COMMANDDISPATCHER);
        commands.add(CAPACMDHANDLER);
        commands.add(USERCMDHANDLER);
        commands.add(PASSCMDHANDLER);
        commands.add(LISTCMDHANDLER);
        commands.add(UIDLCMDHANDLER);
        commands.add(RSETCMDHANDLER);
        commands.add(DELECMDHANDLER);
        commands.add(NOOPCMDHANDLER);
        commands.add(RETRSCMDHANDLER);
        commands.add(TOPCMDHANDLER);
        commands.add(STATCMDHANDLER);
        commands.add(QUITCMDHANDLER);
        commands.add(UNKOWNCMDHANDLER);
        // add STARTTLS support to the core. See JAMES-1224
        commands.add(STLSCMDHANDLER);

        // Add logging stuff
        commands.add(COMMANDHANDLERRESULTLOGGER);
    }

    /**
     */
    public List<String> getHandlers() {
        return commands;
    }

    @Override
    public void init(Configuration config) throws ConfigurationException {

    }

    @Override
    public void destroy() {

    }
}
