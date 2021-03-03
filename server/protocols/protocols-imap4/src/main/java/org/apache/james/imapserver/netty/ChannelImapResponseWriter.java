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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;

import org.apache.james.imap.encode.ImapResponseWriter;
import org.apache.james.imap.message.Literal;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.DefaultFileRegion;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.compression.ZlibEncoder;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedNioFile;
import org.jboss.netty.handler.stream.ChunkedStream;

/**
 * {@link ImapResponseWriter} implementation which writes the data to a
 * {@link Channel}
 */
public class ChannelImapResponseWriter implements ImapResponseWriter {

    private final Channel channel;
    private final boolean zeroCopy;

    public ChannelImapResponseWriter(Channel channel) {
        this(channel, true);
    }

    public ChannelImapResponseWriter(Channel channel, boolean zeroCopy) {
        this.channel = channel;
        this.zeroCopy = zeroCopy;
    }

    @Override
    public void write(byte[] buffer) throws IOException {
        if (channel.isConnected()) {
            channel.write(ChannelBuffers.wrappedBuffer(buffer));
        }
    }

    @Override
    public void write(Literal literal) throws IOException {
        if (channel.isConnected()) {
            InputStream in = literal.getInputStream();
            if (in instanceof FileInputStream && channel.getFactory() instanceof NioServerSocketChannelFactory) {
                FileChannel fc = ((FileInputStream) in).getChannel();
                // Zero-copy is only possible if no SSL/TLS  and no COMPRESS is in place
                //
                // See JAMES-1305 and JAMES-1306
                ChannelPipeline cp = channel.getPipeline();
                if (zeroCopy && cp.get(SslHandler.class) == null && cp.get(ZlibEncoder.class) == null) {
                    channel.write(new DefaultFileRegion(fc, fc.position(), literal.size()));
                } else {
                    channel.write(new ChunkedNioFile(fc, 8192));
                }
            } else {
                channel.write(new ChunkedStream(literal.getInputStream()));
            }
        }
    }
    
    

}
