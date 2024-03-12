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
package org.apache.james.protocols.smtp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.handler.CommandDispatcher;
import org.apache.james.protocols.api.handler.CommandHandlerResultLogger;
import org.apache.james.protocols.api.handler.ExtensibleHandler;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.api.handler.ProtocolHandlerChain;
import org.apache.james.protocols.api.handler.ProtocolHandlerChainImpl;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.smtp.core.DataCmdHandler;
import org.apache.james.protocols.smtp.core.DataLineMessageHookHandler;
import org.apache.james.protocols.smtp.core.ExpnCmdHandler;
import org.apache.james.protocols.smtp.core.HeloCmdHandler;
import org.apache.james.protocols.smtp.core.HelpCmdHandler;
import org.apache.james.protocols.smtp.core.MailCmdHandler;
import org.apache.james.protocols.smtp.core.NoopCmdHandler;
import org.apache.james.protocols.smtp.core.PostmasterAbuseRcptHook;
import org.apache.james.protocols.smtp.core.QuitCmdHandler;
import org.apache.james.protocols.smtp.core.RcptCmdHandler;
import org.apache.james.protocols.smtp.core.ReceivedDataLineFilter;
import org.apache.james.protocols.smtp.core.RsetCmdHandler;
import org.apache.james.protocols.smtp.core.UnknownCmdHandler;
import org.apache.james.protocols.smtp.core.VrfyCmdHandler;
import org.apache.james.protocols.smtp.core.WelcomeMessageHandler;
import org.apache.james.protocols.smtp.core.esmtp.AuthCmdHandler;
import org.apache.james.protocols.smtp.core.esmtp.EhloCmdHandler;
import org.apache.james.protocols.smtp.core.esmtp.MailSizeEsmtpExtension;
import org.apache.james.protocols.smtp.core.esmtp.StartTlsCmdHandler;
import org.apache.james.protocols.smtp.hook.AuthHook;
import org.apache.james.protocols.smtp.hook.Hook;

/**
 * This {@link ProtocolHandlerChain} implementation add all needed handlers to
 * the chain to act as full blown SMTPServer. By default messages will just get
 * rejected after the DATA command.
 */
public class SMTPProtocolHandlerChain extends ProtocolHandlerChainImpl {

    private final MetricFactory metricFactory;

    public SMTPProtocolHandlerChain(MetricFactory metricFactory) {
        this(metricFactory, true);
    }

        
    public SMTPProtocolHandlerChain(MetricFactory metricFactory, boolean addDefault) {
        this.metricFactory = metricFactory;
        if (addDefault) {
            addAll(initDefaultHandlers());      
        }
    }

    /**
     * Add all default handlers to the chain and the given {@link Hook}'s. After that {@link #wireExtensibleHandlers()} is called
     */
    public SMTPProtocolHandlerChain(MetricFactory metricFactory, Hook... hooks) throws WiringException {
        this(metricFactory, true);
        this.addAll(Arrays.asList(hooks));
        wireExtensibleHandlers();
    }
    
    protected List<ProtocolHandler> initDefaultHandlers() {
        List<ProtocolHandler> defaultHandlers = new ArrayList<>();
        defaultHandlers.add(new CommandDispatcher<SMTPSession>());
        defaultHandlers.add(new ExpnCmdHandler());
        defaultHandlers.add(new EhloCmdHandler(metricFactory));
        defaultHandlers.add(new HeloCmdHandler(metricFactory));
        defaultHandlers.add(new HelpCmdHandler());
        defaultHandlers.add(new MailCmdHandler(metricFactory));
        defaultHandlers.add(new NoopCmdHandler());
        defaultHandlers.add(new QuitCmdHandler(metricFactory));
        defaultHandlers.add(new RcptCmdHandler(metricFactory));
        defaultHandlers.add(new RsetCmdHandler());
        defaultHandlers.add(new VrfyCmdHandler());
        defaultHandlers.add(new DataCmdHandler(metricFactory));
        defaultHandlers.add(new MailSizeEsmtpExtension());
        defaultHandlers.add(new WelcomeMessageHandler());
        defaultHandlers.add(new PostmasterAbuseRcptHook());
        defaultHandlers.add(new ReceivedDataLineFilter());
        defaultHandlers.add(new DataLineMessageHookHandler());
        defaultHandlers.add(new StartTlsCmdHandler());
        defaultHandlers.add(new UnknownCmdHandler(metricFactory));
        defaultHandlers.add(new CommandHandlerResultLogger());
        return defaultHandlers;
    }

    private synchronized boolean checkForAuth(ProtocolHandler handler) {
        if (isReadyOnly()) {
            throw new UnsupportedOperationException("Read-Only");
        }
        if (handler instanceof AuthHook) {
            // check if we need to add the AuthCmdHandler
            List<ExtensibleHandler> handlers = getHandlers(ExtensibleHandler.class);
            for (ExtensibleHandler h: handlers) {
                if (h.getMarkerInterfaces().contains(AuthHook.class)) {
                    return true;
                }
            }
            if (!add(new AuthCmdHandler())) {
                return false;
            }
        }
        return true;
    }
    
    @Override
    public boolean add(ProtocolHandler handler) {
        checkForAuth(handler);
        return super.add(handler);
    }

    @Override
    public boolean addAll(Collection<? extends ProtocolHandler> c) {
        return c.stream().allMatch(this::checkForAuth) && super.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends ProtocolHandler> c) {
        return c.stream().allMatch(this::checkForAuth) && super.addAll(index, c);
    }

    @Override
    public void add(int index, ProtocolHandler element) {
        checkForAuth(element);
        super.add(index, element);
    }
  
}
