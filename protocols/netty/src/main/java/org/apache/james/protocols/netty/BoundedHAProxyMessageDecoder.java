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

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.codec.haproxy.HAProxyProtocolException;

/**
 * {@link HAProxyMessageDecoder} refusing to buffer more than the longest legal PROXY protocol header.
 *
 * Some inputs, like a PROXY protocol v2 signature followed by a 0xFF version byte (CVE-2026-55851),
 * make the Netty decoder wait for more data forever, buffering everything the client sends until
 * direct memory is exhausted.
 */
public class BoundedHAProxyMessageDecoder extends HAProxyMessageDecoder {
    // PROXY protocol v2: 16 bytes header followed by at most 65535 bytes of addresses and TLVs. v1 is at most 108 bytes.
    static final int MAX_HEADER_LENGTH = 16 + 65535;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead(ctx, msg);
        if (internalBuffer().readableBytes() > MAX_HEADER_LENGTH) {
            ctx.close();
            throw new HAProxyProtocolException("PROXY protocol header exceeds " + MAX_HEADER_LENGTH + " bytes");
        }
    }
}
