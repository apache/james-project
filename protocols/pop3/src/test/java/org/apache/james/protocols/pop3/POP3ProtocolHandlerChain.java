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

package org.apache.james.protocols.pop3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.protocols.api.handler.CommandDispatcher;
import org.apache.james.protocols.api.handler.CommandHandlerResultLogger;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.api.handler.ProtocolHandlerChainImpl;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.pop3.core.AbstractPassCmdHandler;
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

/**
 * {@link ProtocolHandlerChainImpl} which allows to add the default handlers which are needed to server POP3.
 * 
 *
 */
public class POP3ProtocolHandlerChain extends ProtocolHandlerChainImpl {
    private final MetricFactory metricFactory = new RecordingMetricFactory();

    /**
     * The {@link AbstractPassCmdHandler}'s to use. If at least one {@link AbstractPassCmdHandler} is given, the {@link POP3ProtocolHandlerChain}
     * will add all default handlers
     */
    public POP3ProtocolHandlerChain(AbstractPassCmdHandler... authHandlers) throws WiringException {
        if (authHandlers != null && authHandlers.length > 0) {
            addAll(initDefaultHandlers(authHandlers));      
            wireExtensibleHandlers();
        }
    }
    
    protected List<ProtocolHandler> initDefaultHandlers(AbstractPassCmdHandler... authHandlers) {
        List<ProtocolHandler> handlers = new ArrayList<>();
        // add all pass handlers
        Collections.addAll(handlers, authHandlers);
        
        handlers.add(new CapaCmdHandler(metricFactory));
        handlers.add(new UserCmdHandler(metricFactory));
        handlers.add(new ListCmdHandler(metricFactory));
        handlers.add(new UidlCmdHandler(metricFactory));
        handlers.add(new RsetCmdHandler(metricFactory));
        handlers.add(new DeleCmdHandler(metricFactory));
        handlers.add(new NoopCmdHandler(metricFactory));
        handlers.add(new RetrCmdHandler(metricFactory));
        handlers.add(new TopCmdHandler(metricFactory));
        handlers.add(new StatCmdHandler(metricFactory));
        handlers.add(new QuitCmdHandler(metricFactory));
        handlers.add(new StlsCmdHandler(metricFactory));
        handlers.add(new WelcomeMessageHandler());
        handlers.add(new UnknownCmdHandler());
        handlers.add(new CommandDispatcher<POP3Session>());
        handlers.add(new CommandHandlerResultLogger());
       
        return handlers;
    }
}
