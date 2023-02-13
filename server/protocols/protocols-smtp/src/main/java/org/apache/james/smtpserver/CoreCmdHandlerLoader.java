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

package org.apache.james.smtpserver;

import java.util.List;

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

/**
 * This class represent the base command handlers which are shipped with james.
 */
public class CoreCmdHandlerLoader implements HandlersPackage {

    private static final List<String> commands = List.of(
            JamesWelcomeMessageHandler.class.getName(),
            CommandDispatcher.class.getName(),
            AuthCmdHandler.class.getName(),
            JamesDataCmdHandler.class.getName(),
            EhloCmdHandler.class.getName(),
            ExpnCmdHandler.class.getName(),
            HeloCmdHandler.class.getName(),
            HelpCmdHandler.class.getName(),
            JamesMailCmdHandler.class.getName(),
            NoopCmdHandler.class.getName(),
            QuitCmdHandler.class.getName(),
            JamesRcptCmdHandler.class.getName(),
            RsetCmdHandler.class.getName(),
            VrfyCmdHandler.class.getName(),
            MailSizeEsmtpExtension.class.getName(),
            UsersRepositoryAuthHook.class.getName(),
            AuthRequiredToRelayRcptHook.class.getName(),
            SenderAuthIdentifyVerificationHook.class.getName(),
            PostmasterAbuseRcptHook.class.getName(),
            ReceivedDataLineFilter.class.getName(),
            DataLineJamesMessageHookHandler.class.getName(),
            StartTlsCmdHandler.class.getName(),
            AddDefaultAttributesMessageHook.class.getName(),
            SendMailHandler.class.getName(),
            UnknownCmdHandler.class.getName(),
            // Add logging stuff
            CommandHandlerResultLogger.class.getName(),
            HookResultLogger.class.getName());

    public CoreCmdHandlerLoader() {
    }

    @Override
    public List<String> getHandlers() {
        return commands;
    }
}
