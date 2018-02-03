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

import org.apache.james.imap.api.process.ImapLineHandler;
import org.apache.james.imap.api.process.ImapSession;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

/**
 * {@link SimpleChannelUpstreamHandler} implementation which will delegate the
 * data received on
 * {@link #messageReceived(ChannelHandlerContext, MessageEvent)} to a
 * {@link ImapLineHandler#onLine(ImapSession, byte[])}
 */
public class ImapLineHandlerAdapter extends SimpleChannelUpstreamHandler {

    private final ImapLineHandler lineHandler;
    private final ImapSession session;

    public ImapLineHandlerAdapter(ImapSession session, ImapLineHandler lineHandler) {
        this.lineHandler = lineHandler;
        this.session = session;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        ChannelBuffer buf = (ChannelBuffer) e.getMessage();
        byte[] data;
        if (buf.hasArray()) {
            data = buf.array();
        } else {
            data = new byte[buf.readableBytes()];
            buf.readBytes(data);
        }
        lineHandler.onLine(session, data);
    }

}
