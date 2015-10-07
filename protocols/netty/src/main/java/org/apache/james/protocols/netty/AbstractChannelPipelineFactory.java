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

import static org.jboss.netty.channel.Channels.pipeline;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import org.jboss.netty.handler.codec.frame.Delimiters;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.jboss.netty.util.ExternalResourceReleasable;
import org.jboss.netty.util.HashedWheelTimer;

/**
 * Abstract base class for {@link ChannelPipelineFactory} implementations
 * 
 *
 */
public abstract class AbstractChannelPipelineFactory implements ChannelPipelineFactory, ExternalResourceReleasable{

    public final static int MAX_LINE_LENGTH = 8192;
    protected final ConnectionLimitUpstreamHandler connectionLimitHandler;
    protected final ConnectionPerIpLimitUpstreamHandler connectionPerIpLimitHandler;
    private final HashedWheelTimer timer = new HashedWheelTimer();
    private final ChannelGroupHandler groupHandler;
	private final int timeout;
    private final ExecutionHandler eHandler;
    public AbstractChannelPipelineFactory(int timeout, int maxConnections, int maxConnectsPerIp, ChannelGroup channels) {
        this(timeout, maxConnections, maxConnectsPerIp, channels, null);
    }
    
    public AbstractChannelPipelineFactory(int timeout, int maxConnections, int maxConnectsPerIp, ChannelGroup channels, ExecutionHandler eHandler) {
        this.connectionLimitHandler = new ConnectionLimitUpstreamHandler(maxConnections);
        this.connectionPerIpLimitHandler = new ConnectionPerIpLimitUpstreamHandler(maxConnectsPerIp);
        this.groupHandler = new ChannelGroupHandler(channels);
        this.timeout = timeout;
        this.eHandler = eHandler;
    }
    
    
    
    
    /**
     * @see org.jboss.netty.channel.ChannelPipelineFactory#getPipeline()
     */
    public ChannelPipeline getPipeline() throws Exception {
        // Create a default pipeline implementation.
        ChannelPipeline pipeline = pipeline();
        pipeline.addLast(HandlerConstants.GROUP_HANDLER, groupHandler);

        pipeline.addLast(HandlerConstants.CONNECTION_LIMIT_HANDLER, connectionLimitHandler);

        pipeline.addLast(HandlerConstants.CONNECTION_PER_IP_LIMIT_HANDLER, connectionPerIpLimitHandler);

        
        // Add the text line decoder which limit the max line length, don't strip the delimiter and use CRLF as delimiter
        pipeline.addLast(HandlerConstants.FRAMER, new DelimiterBasedFrameDecoder(MAX_LINE_LENGTH, false, Delimiters.lineDelimiter()));
       
        // Add the ChunkedWriteHandler to be able to write ChunkInput
        pipeline.addLast(HandlerConstants.CHUNK_HANDLER, new ChunkedWriteHandler());
        pipeline.addLast(HandlerConstants.TIMEOUT_HANDLER, new TimeoutHandler(timer, timeout));

        if (eHandler != null) {
            pipeline.addLast(HandlerConstants.EXECUTION_HANDLER, eHandler);
        }
        
        pipeline.addLast(HandlerConstants.CORE_HANDLER, createHandler());


        return pipeline;
    }



    
    /**
     * Create the core {@link ChannelUpstreamHandler} to use
     * 
     * @return coreHandeler
     */
    protected abstract ChannelUpstreamHandler createHandler();

    
    /*
     * (non-Javadoc)
     * @see org.jboss.netty.util.ExternalResourceReleasable#releaseExternalResources()
     */
    public void releaseExternalResources() {
        timer.stop();
    }
}
