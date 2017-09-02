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

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
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

    private final List<String> commands = new LinkedList<>();

    public CoreCmdHandlerLoader() {
        // Insert the base commands in the Map
        String WELCOMEMESSAGEHANDLER = JamesWelcomeMessageHandler.class.getName();
        commands.add(WELCOMEMESSAGEHANDLER);
        String COMMANDDISPATCHER = CommandDispatcher.class.getName();
        commands.add(COMMANDDISPATCHER);
        String AUTHCMDHANDLER = AuthCmdHandler.class.getName();
        commands.add(AUTHCMDHANDLER);
        String DATACMDHANDLER = JamesDataCmdHandler.class.getName();
        commands.add(DATACMDHANDLER);
        String EHLOCMDHANDLER = EhloCmdHandler.class.getName();
        commands.add(EHLOCMDHANDLER);
        String EXPNCMDHANDLER = ExpnCmdHandler.class.getName();
        commands.add(EXPNCMDHANDLER);
        String HELOCMDHANDLER = HeloCmdHandler.class.getName();
        commands.add(HELOCMDHANDLER);
        String HELPCMDHANDLER = HelpCmdHandler.class.getName();
        commands.add(HELPCMDHANDLER);
        String MAILCMDHANDLER = JamesMailCmdHandler.class.getName();
        commands.add(MAILCMDHANDLER);
        String NOOPCMDHANDLER = NoopCmdHandler.class.getName();
        commands.add(NOOPCMDHANDLER);
        String QUITCMDHANDLER = QuitCmdHandler.class.getName();
        commands.add(QUITCMDHANDLER);
        String RCPTCMDHANDLER = JamesRcptCmdHandler.class.getName();
        commands.add(RCPTCMDHANDLER);
        String RSETCMDHANDLER = RsetCmdHandler.class.getName();
        commands.add(RSETCMDHANDLER);
        String VRFYCMDHANDLER = VrfyCmdHandler.class.getName();
        commands.add(VRFYCMDHANDLER);
        String MAILSIZEHOOK = MailSizeEsmtpExtension.class.getName();
        commands.add(MAILSIZEHOOK);
        String USERSREPOSITORYAUTHHANDLER = UsersRepositoryAuthHook.class.getName();
        commands.add(USERSREPOSITORYAUTHHANDLER);
        String AUTHREQUIREDTORELAY = AuthRequiredToRelayRcptHook.class.getName();
        commands.add(AUTHREQUIREDTORELAY);
        String SENDERAUTHIDENTITYVERIFICATION = SenderAuthIdentifyVerificationRcptHook.class.getName();
        commands.add(SENDERAUTHIDENTITYVERIFICATION);
        String POSTMASTERABUSEHOOK = PostmasterAbuseRcptHook.class.getName();
        commands.add(POSTMASTERABUSEHOOK);
        String RECEIVEDDATALINEFILTER = ReceivedDataLineFilter.class.getName();
        commands.add(RECEIVEDDATALINEFILTER);
        String DATALINEMESSAGEHOOKHANDLER = DataLineJamesMessageHookHandler.class.getName();
        commands.add(DATALINEMESSAGEHOOKHANDLER);
        String STARTTLSHANDLER = StartTlsCmdHandler.class.getName();
        commands.add(STARTTLSHANDLER);
        // Add the default messageHooks
        String ADDDEFAULTATTRIBUTESHANDLER = AddDefaultAttributesMessageHook.class.getName();
        commands.add(ADDDEFAULTATTRIBUTESHANDLER);
        String SENDMAILHANDLER = SendMailHandler.class.getName();
        commands.add(SENDMAILHANDLER);
        String UNKNOWNCMDHANDLER = UnknownCmdHandler.class.getName();
        commands.add(UNKNOWNCMDHANDLER);

        // Add logging stuff
        String COMMANDHANDLERRESULTLOGGER = CommandHandlerResultLogger.class.getName();
        commands.add(COMMANDHANDLERRESULTLOGGER);
        String HOOKRESULTLOGGER = HookResultLogger.class.getName();
        commands.add(HOOKRESULTLOGGER);
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
