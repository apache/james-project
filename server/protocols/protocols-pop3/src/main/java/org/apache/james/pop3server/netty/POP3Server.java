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
package org.apache.james.pop3server.netty;

import org.apache.james.pop3server.core.CoreCmdHandlerLoader;
import org.apache.james.pop3server.jmx.JMXHandlersLoader;
import org.apache.james.protocols.api.ProtocolConfiguration;
import org.apache.james.protocols.lib.handler.HandlersPackage;
import org.apache.james.protocols.lib.netty.AbstractProtocolAsyncServer;
import org.apache.james.protocols.netty.AbstractChannelPipelineFactory;
import org.apache.james.protocols.netty.BasicChannelUpstreamHandler;
import org.apache.james.protocols.netty.ChannelHandlerFactory;
import org.apache.james.protocols.netty.LineDelimiterBasedChannelHandlerFactory;
import org.apache.james.protocols.netty.ProtocolMDCContextFactory;
import org.apache.james.protocols.pop3.POP3Protocol;
import org.jboss.netty.channel.ChannelUpstreamHandler;

/**
 * NIO POP3 Server which use Netty
 */
public class POP3Server extends AbstractProtocolAsyncServer implements POP3ServerMBean {

    /**
     * The configuration data to be passed to the handler
     */
    private final ProtocolConfiguration theConfigData = new POP3Configuration();
    private BasicChannelUpstreamHandler coreHandler;
    
    @Override
    protected int getDefaultPort() {
        return 110;
    }

    @Override
    public String getServiceType() {
        return "POP3 Service";
    }

    /**
     * A class to provide POP3 handler configuration to the handlers
     */
    private class POP3Configuration implements ProtocolConfiguration {
        @Override
        public String getHelloName() {
            return POP3Server.this.getHelloName();
        }

        @Override
        public String getGreeting() {
            return null;
        }

        @Override
        public String getSoftwareName() {
            return "JAMES POP3 Server ";
        }
    }

    @Override
    protected void preInit() throws Exception {
        super.preInit();
        POP3Protocol protocol = new POP3Protocol(getProtocolHandlerChain(), theConfigData);
        coreHandler = new BasicChannelUpstreamHandler(new ProtocolMDCContextFactory.Standard(), protocol, getEncryption());
    }

    @Override
    protected String getDefaultJMXName() {
        return "pop3server";
    }

    @Override
    protected ChannelUpstreamHandler createCoreHandler() {
        return coreHandler; 
    }

    @Override
    protected Class<? extends HandlersPackage> getCoreHandlersPackage() {
        return CoreCmdHandlerLoader.class;
    }

    @Override
    protected Class<? extends HandlersPackage> getJMXHandlersPackage() {
        return JMXHandlersLoader.class;
    }

    @Override
    protected ChannelHandlerFactory createFrameHandlerFactory() {
        return new LineDelimiterBasedChannelHandlerFactory(AbstractChannelPipelineFactory.MAX_LINE_LENGTH);
    }

}
