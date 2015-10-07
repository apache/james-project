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
package org.apache.james.imapserver.netty;

import org.jboss.netty.channel.ChannelLocal;

/**
 * Just some constants which are used with the Netty implementation
 */
public interface NettyConstants {
    final static String ZLIB_DECODER = "zlibDecoder";
    final static String ZLIB_ENCODER = "zlibEncoder";
    final static String SSL_HANDLER = "sslHandler";
    final static String REQUEST_DECODER = "requestDecoder";
    final static String FRAMER = "framer";
    final static String TIMEOUT_HANDLER = "timeoutHandler";
    final static String CORE_HANDLER = "coreHandler";
    final static String GROUP_HANDLER = "groupHandler";
    final static String CONNECTION_LIMIT_HANDLER = "connectionLimitHandler";
    final static String CONNECTION_LIMIT_PER_IP_HANDLER = "connectionPerIpLimitHandler";
    final static String CONNECTION_COUNT_HANDLER = "connectionCountHandler";
    final static String CHUNK_WRITE_HANDLER = "chunkWriteHandler";
    final static String EXECUTION_HANDLER = "executionHandler";
    final static String HEARTBEAT_HANDLER = "heartbeatHandler";

    final static ChannelLocal<Object> attributes = new ChannelLocal<Object>();
}
