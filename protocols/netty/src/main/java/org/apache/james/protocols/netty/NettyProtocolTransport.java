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

import javax.net.ssl.SSLEngine;

import org.apache.james.protocols.api.AbstractProtocolTransport;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.handler.LineHandler;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.DefaultFileRegion;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedStream;

/**
 * A Netty implementation of a ProtocolTransport
 */
public class NettyProtocolTransport extends AbstractProtocolTransport {
    
    private final Channel channel;
    private final SSLEngine engine;
    private int lineHandlerCount = 0;
    
    public NettyProtocolTransport(Channel channel, SSLEngine engine) {
        this.channel = channel;
        this.engine = engine;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return (InetSocketAddress) channel.getRemoteAddress();
    }

    @Override
    public String getId() {
        return Integer.toString(channel.getId());
    }

    @Override
    public boolean isTLSStarted() {
        return channel.getPipeline().get(SslHandler.class) != null;
    }

    @Override
    public boolean isStartTLSSupported() {
        return engine != null;
    }


    @Override
    public void popLineHandler() {
        if (lineHandlerCount > 0) {
            channel.getPipeline().remove("lineHandler" + lineHandlerCount);
            lineHandlerCount--;
        }
    }

    @Override
    public int getPushedLineHandlerCount() {
        return lineHandlerCount;
    }

    /**
     * Add the {@link SslHandler} to the pipeline and start encrypting after the next written message
     */
    private void prepareStartTLS() {
        SslHandler filter = new SslHandler(engine, true);
        filter.getEngine().setUseClientMode(false);
        channel.getPipeline().addFirst(HandlerConstants.SSL_HANDLER, filter);
    }

    @Override
    protected void writeToClient(byte[] bytes, ProtocolSession session, boolean startTLS) {
        if (startTLS) {
            prepareStartTLS();
        }
        channel.write(ChannelBuffers.wrappedBuffer(bytes));
        
    }

    @Override
    protected void close() {
        channel.write(ChannelBuffers.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }


    @Override
    protected void writeToClient(InputStream in, ProtocolSession session, boolean startTLS) {
        if (startTLS) {
            prepareStartTLS();
        }
        if (!isTLSStarted()) {
            if (in instanceof FileInputStream) {
                FileChannel fChannel = ((FileInputStream) in).getChannel();
                try {
                    channel.write(new DefaultFileRegion(fChannel, 0, fChannel.size(), true));

                } catch (IOException e) {
                    // We handle this later
                    channel.write(new ChunkedStream(new ExceptionInputStream(e)));
                }
                return;
            }
        }
        channel.write(new ChunkedStream(in));
    }

    @Override
    public void setReadable(boolean readable) {
        channel.setReadable(readable);
    }

    @Override
    public boolean isReadable() {
        return channel.isReadable();
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return (InetSocketAddress) channel.getLocalAddress();
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void pushLineHandler(LineHandler<? extends ProtocolSession> overrideCommandHandler, ProtocolSession session) {
        lineHandlerCount++;
        // Add the linehandler in front of the coreHandler so we can be sure 
        // it is executed with the same ExecutorHandler as the coreHandler (if one exist)
        // 
        // See JAMES-1277
        channel.getPipeline().addBefore(HandlerConstants.CORE_HANDLER, "lineHandler" + lineHandlerCount, new LineHandlerUpstreamHandler(session, overrideCommandHandler));
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
