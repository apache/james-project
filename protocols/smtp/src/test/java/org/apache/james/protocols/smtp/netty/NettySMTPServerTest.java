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
package org.apache.james.protocols.smtp.netty;

import java.net.InetSocketAddress;

import org.apache.james.protocols.api.Encryption;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.ProtocolServer;
import org.apache.james.protocols.netty.AbstractChannelPipelineFactory;
import org.apache.james.protocols.netty.NettyServer;
import org.apache.james.protocols.smtp.AbstractSMTPServerTest;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.Delimiters;

/**
 * Integration tests which use netty implementation
 * 
 *
 */
public class NettySMTPServerTest extends AbstractSMTPServerTest{

    
    @Override
    protected ProtocolServer createServer(Protocol protocol, InetSocketAddress address) {
        Encryption secure = null;
        NettyServer server = new NettyServer(protocol, secure,
                new DelimiterBasedFrameDecoder(AbstractChannelPipelineFactory.MAX_LINE_LENGTH, false, Delimiters.lineDelimiter()));
        server.setListenAddresses(address);
        return server;
    }
}
