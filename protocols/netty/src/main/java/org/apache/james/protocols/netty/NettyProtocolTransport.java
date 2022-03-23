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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;

import org.apache.james.protocols.api.AbstractProtocolTransport;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.handler.LineHandler;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedStream;


/**
 * A Netty implementation of a ProtocolTransport
 */
public class NettyProtocolTransport extends AbstractProtocolTransport {
    
    private final Channel channel;
    private final Encryption encryption;
    
    public NettyProtocolTransport(Channel channel, Encryption encryption) {
        this.channel = channel;
        this.encryption = encryption;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return (InetSocketAddress) channel.remoteAddress();
    }

    @Override
    public String getId() {
        return channel.id().toString();
    }

    @Override
    public boolean isTLSStarted() {
        return channel.pipeline().get(SslHandler.class) != null;
    }

    @Override
    public boolean isStartTLSSupported() {
        return encryption != null && encryption.isStartTLS();
    }


    @Override
    public void popLineHandler() {
        LineHandlerAware channelHandler = (LineHandlerAware) channel.pipeline()
            .get(HandlerConstants.CORE_HANDLER);
        channelHandler.popLineHandler();
    }

    /**
     * Add the {@link SslHandler} to the pipeline and start encrypting after the next written message
     */
    private void prepareStartTLS() {
        channel.pipeline().addFirst(HandlerConstants.SSL_HANDLER, encryption.sslHandler());
    }

    @Override
    protected void writeToClient(byte[] bytes, ProtocolSession session, boolean startTLS) {
        if (startTLS) {
            prepareStartTLS();
        }

        channel.writeAndFlush(Unpooled.wrappedBuffer(bytes));
    }

    @Override
    protected void close() {
        channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }


    @Override
    protected void writeToClient(InputStream in, ProtocolSession session, boolean startTLS) {
        if (startTLS) {
            prepareStartTLS();
        }
        if (!isTLSStarted() && in instanceof FileInputStream) {
            FileChannel fChannel = ((FileInputStream) in).getChannel();
            try {
                channel.writeAndFlush(new DefaultFileRegion(fChannel, 0, fChannel.size()));

            } catch (IOException e) {
                // We handle this later
                channel.writeAndFlush(new ChunkedStream(new ExceptionInputStream(e)));
            }
            return;
        }
        channel.writeAndFlush(new ChunkedStream(in));
    }

    @Override
    public void setReadable(boolean readable) {
        channel.config().setAutoRead(readable);
    }

    @Override
    public boolean isReadable() {
        return channel.config().isAutoRead();
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return (InetSocketAddress) channel.localAddress();
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void pushLineHandler(LineHandler<? extends ProtocolSession> overrideCommandHandler, ProtocolSession session) {
        LineHandlerAware channelHandler = (LineHandlerAware) channel.pipeline()
            .get(HandlerConstants.CORE_HANDLER);
        channelHandler.pushLineHandler(new LineHandlerUpstreamHandler(session, overrideCommandHandler));
    }
    
   
    /**
     * {@link InputStream} which just re-throw the {@link IOException} on the next {@link #read()} operation.
     * 
     *
     */
    private static final class ExceptionInputStream extends InputStream {
        private final IOException e;

        public ExceptionInputStream(IOException e) {
            this.e = e;
        }
        
        @Override
        public int read() throws IOException {
            throw e;
        }
        
    }

}
