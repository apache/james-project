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

package org.apache.james.examples;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.james.protocols.api.handler.CommandDispatcher;
import org.apache.james.protocols.api.handler.CommandHandlerResultLogger;
import org.apache.james.protocols.lib.handler.HandlersPackage;
import org.apache.james.protocols.smtp.core.ExpnCmdHandler;
import org.apache.james.protocols.smtp.core.HeloCmdHandler;
import org.apache.james.protocols.smtp.core.HelpCmdHandler;
import org.apache.james.protocols.smtp.core.NoopCmdHandler;
import org.apache.james.protocols.smtp.core.PostmasterAbuseRcptHook;
import org.apache.james.protocols.smtp.core.QuitCmdHandler;
import org.apache.james.protocols.smtp.core.ReceivedDataLineFilter;
import org.apache.james.protocols.smtp.core.RsetCmdHandler;
import org.apache.james.protocols.smtp.core.UnknownCmdHandler;
import org.apache.james.protocols.smtp.core.VrfyCmdHandler;
import org.apache.james.protocols.smtp.core.esmtp.AuthCmdHandler;
import org.apache.james.protocols.smtp.core.esmtp.EhloCmdHandler;
import org.apache.james.protocols.smtp.core.esmtp.MailSizeEsmtpExtension;
import org.apache.james.protocols.smtp.core.esmtp.StartTlsCmdHandler;
import org.apache.james.protocols.smtp.core.log.HookResultLogger;
import org.apache.james.smtpserver.AddDefaultAttributesMessageHook;
import org.apache.james.smtpserver.AuthRequiredToRelayRcptHook;
import org.apache.james.smtpserver.DataLineJamesMessageHookHandler;
import org.apache.james.smtpserver.JamesDataCmdHandler;
import org.apache.james.smtpserver.JamesMailCmdHandler;
import org.apache.james.smtpserver.JamesRcptCmdHandler;
import org.apache.james.smtpserver.JamesWelcomeMessageHandler;
import org.apache.james.smtpserver.SendMailHandler;
import org.apache.james.smtpserver.SenderAuthIdentifyVerificationRcptHook;
import org.apache.james.smtpserver.UsersRepositoryAuthHook;

/**
 * This class copies CoreCmdHandlerLoader adding support for MYNOOP command
 */
public class MyCmdHandlerLoader implements HandlersPackage {

    private final List<String> commands = new LinkedList<>();

    public MyCmdHandlerLoader() {
        Stream.of(
            JamesWelcomeMessageHandler.class,
            CommandDispatcher.class,
            AuthCmdHandler.class,
            JamesDataCmdHandler.class,
            EhloCmdHandler.class,
            ExpnCmdHandler.class,
            HeloCmdHandler.class,
            HelpCmdHandler.class,
            JamesMailCmdHandler.class,
            NoopCmdHandler.class,
            QuitCmdHandler.class,
            JamesRcptCmdHandler.class,
            RsetCmdHandler.class,
            VrfyCmdHandler.class,
            MailSizeEsmtpExtension.class,
            UsersRepositoryAuthHook.class,
            AuthRequiredToRelayRcptHook.class,
            SenderAuthIdentifyVerificationRcptHook.class,
            PostmasterAbuseRcptHook.class,
            ReceivedDataLineFilter.class,
            DataLineJamesMessageHookHandler.class,
            StartTlsCmdHandler.class,
            AddDefaultAttributesMessageHook.class,
            SendMailHandler.class,
            UnknownCmdHandler.class,
            CommandHandlerResultLogger.class,
            HookResultLogger.class,
            // Support MYNOOP
            MyNoopCmdHandler.class)
        .map(Class::getName)
        .forEachOrdered(commands::add);
    }

    @Override
    public List<String> getHandlers() {
        return commands;
    }
}
