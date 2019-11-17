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

import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.oio.OioServerSocketChannelFactory;
import org.jboss.netty.handler.execution.ExecutionHandler;

/**
 * POP3Server which use old IO and not NIO. If you want to use NIO you should
 * use {@link POP3Server}
 */
public class OioPOP3Server extends POP3Server {

    @Override
    protected ServerSocketChannelFactory createSocketChannelFactory() {
        return new OioServerSocketChannelFactory(createBossExecutor(), createWorkerExecutor());
    }

    /**
     * As OIO use one thread per connection we disable the use of the {@link ExecutionHandler}
     */
    @Override
    protected ExecutionHandler createExecutionHander() {
        return null;
    }

}
