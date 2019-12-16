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
package org.apache.james.protocols.lmtp;

import java.util.ArrayList;
import java.util.List;

import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.protocols.api.handler.CommandDispatcher;
import org.apache.james.protocols.api.handler.CommandHandlerResultLogger;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.lmtp.core.DataLineMessageHookHandler;
import org.apache.james.protocols.lmtp.core.LhloCmdHandler;
import org.apache.james.protocols.lmtp.core.ReceivedDataLineFilter;
import org.apache.james.protocols.lmtp.core.WelcomeMessageHandler;
import org.apache.james.protocols.smtp.SMTPProtocolHandlerChain;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.core.DataCmdHandler;
import org.apache.james.protocols.smtp.core.ExpnCmdHandler;
import org.apache.james.protocols.smtp.core.HelpCmdHandler;
import org.apache.james.protocols.smtp.core.MailCmdHandler;
import org.apache.james.protocols.smtp.core.NoopCmdHandler;
import org.apache.james.protocols.smtp.core.QuitCmdHandler;
import org.apache.james.protocols.smtp.core.RcptCmdHandler;
import org.apache.james.protocols.smtp.core.RsetCmdHandler;
import org.apache.james.protocols.smtp.core.UnknownCmdHandler;
import org.apache.james.protocols.smtp.core.VrfyCmdHandler;
import org.apache.james.protocols.smtp.core.esmtp.MailSizeEsmtpExtension;
import org.apache.james.protocols.smtp.core.esmtp.StartTlsCmdHandler;

/**
 * Special {@link SMTPProtocolHandlerChain} sub-class which should be used to build the chain for LMTP.
 */
public class LMTPProtocolHandlerChain extends SMTPProtocolHandlerChain {

    public LMTPProtocolHandlerChain() {
        super(new NoopMetricFactory());
    }

    @Override
    protected List<ProtocolHandler> initDefaultHandlers() {
        List<ProtocolHandler> defaultHandlers = new ArrayList<>();
        defaultHandlers.add(new CommandDispatcher<SMTPSession>());
        defaultHandlers.add(new ExpnCmdHandler());
        defaultHandlers.add(new LhloCmdHandler(new NoopMetricFactory()));
        defaultHandlers.add(new HelpCmdHandler());
        defaultHandlers.add(new MailCmdHandler(new NoopMetricFactory()));
        defaultHandlers.add(new NoopCmdHandler());
        defaultHandlers.add(new QuitCmdHandler(new NoopMetricFactory()));
        defaultHandlers.add(new RcptCmdHandler(new NoopMetricFactory()));
        defaultHandlers.add(new RsetCmdHandler());
        defaultHandlers.add(new VrfyCmdHandler());
        defaultHandlers.add(new DataCmdHandler(new NoopMetricFactory()));
        defaultHandlers.add(new MailSizeEsmtpExtension());
        defaultHandlers.add(new WelcomeMessageHandler());
        defaultHandlers.add(new ReceivedDataLineFilter());
        defaultHandlers.add(new DataLineMessageHookHandler());
        defaultHandlers.add(new StartTlsCmdHandler());
        defaultHandlers.add(new UnknownCmdHandler(new NoopMetricFactory()));
        defaultHandlers.add(new CommandHandlerResultLogger());

        return defaultHandlers;
    }

}
