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

package org.apache.james.lmtpserver;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.james.protocols.api.handler.CommandDispatcher;
import org.apache.james.protocols.api.handler.CommandHandlerResultLogger;
import org.apache.james.protocols.lib.handler.HandlersPackage;
import org.apache.james.protocols.lmtp.core.LhloCmdHandler;
import org.apache.james.protocols.lmtp.core.WelcomeMessageHandler;
import org.apache.james.protocols.smtp.core.ExpnCmdHandler;
import org.apache.james.protocols.smtp.core.NoopCmdHandler;
import org.apache.james.protocols.smtp.core.PostmasterAbuseRcptHook;
import org.apache.james.protocols.smtp.core.QuitCmdHandler;
import org.apache.james.protocols.smtp.core.ReceivedDataLineFilter;
import org.apache.james.protocols.smtp.core.RsetCmdHandler;
import org.apache.james.protocols.smtp.core.VrfyCmdHandler;
import org.apache.james.protocols.smtp.core.esmtp.MailSizeEsmtpExtension;
import org.apache.james.protocols.smtp.core.log.HookResultLogger;
import org.apache.james.smtpserver.AuthRequiredToRelayRcptHook;
import org.apache.james.smtpserver.JamesDataCmdHandler;
import org.apache.james.smtpserver.JamesMailCmdHandler;
import org.apache.james.smtpserver.JamesRcptCmdHandler;
import org.apache.james.smtpserver.fastfail.ValidRcptHandler;

/**
 * This class allows creating a LMTP server executing the mailet container
 */
public class MailetContainerCmdHandlerLoader implements HandlersPackage {

    private final List<String> commands = new LinkedList<>();


    public MailetContainerCmdHandlerLoader() {
        Stream.of(
            WelcomeMessageHandler.class,
            CommandDispatcher.class,
            JamesDataCmdHandler.class,
            ExpnCmdHandler.class,
            LhloCmdHandler.class,
            JamesMailCmdHandler.class,
            NoopCmdHandler.class,
            QuitCmdHandler.class,
            JamesRcptCmdHandler.class,
            ValidRcptHandler.class,
            RsetCmdHandler.class,
            VrfyCmdHandler.class,
            MailSizeEsmtpExtension.class,
            AuthRequiredToRelayRcptHook.class,
            PostmasterAbuseRcptHook.class,
            ReceivedDataLineFilter.class,
            MailetContainerHandler.class,
            CommandHandlerResultLogger.class,
            NoopJamesMessageHook.class,
            HookResultLogger.class)
        .map(Class::getName)
        .forEachOrdered(commands::add);
    }

    @Override
    public List<String> getHandlers() {
        return commands;
    }
}
