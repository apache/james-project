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
package org.apache.james.protocols.netty;

import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;

/**
 * Provide the keys under which the {@link ChannelHandler}'s are stored in the
 * {@link ChannelPipeline}
 * 
 * 
 */
public interface HandlerConstants {

    public static final String SSL_HANDLER = "sslHandler";

    public static final String GROUP_HANDLER = "groupHandler";

    public static final String CONNECTION_LIMIT_HANDLER = " connectionLimit";

    public static final String CONNECTION_PER_IP_LIMIT_HANDLER = "connectionPerIpLimit";

    public static final String FRAMER = "framer";

    public static final String EXECUTION_HANDLER = "executionHandler";

    public static final String TIMEOUT_HANDLER = "timeoutHandler";

    public static final String CORE_HANDLER = "coreHandler";

    public static final String CHUNK_HANDLER = "chunkHandler";

}
