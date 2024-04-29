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

import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.ProtocolSessionImpl;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.LineHandler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * {@link ChannelInboundHandlerAdapter} implementation which will call a given {@link LineHandler} implementation
 *
 * @param <S>
 */
@ChannelHandler.Sharable
public class LineHandlerUpstreamHandler<S extends ProtocolSession> extends ChannelInboundHandlerAdapter {
    private final LineHandler<S> handler;
    private final S session;
    
    public LineHandlerUpstreamHandler(S session, LineHandler<S> handler) {
        this.handler = handler;
        this.session = session;
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
        try {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.getBytes(0, bytes);
            Response response = handler.onLine(session, bytes);
            if (response != null) {
                // TODO: This kind of sucks but I was not able to come up with something more elegant here
                ((ProtocolSessionImpl) session).getProtocolTransport().writeResponse(response, session);
            }
        } finally {
            buf.release();
        }
    }

}
