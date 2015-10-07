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

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.frame.DelimiterBasedFrameDecoder;

public class SwitchableDelimiterBasedFrameDecoder extends DelimiterBasedFrameDecoder {

	private volatile boolean framingEnabled = true;
	private volatile ChannelBuffer cumulation;

	public SwitchableDelimiterBasedFrameDecoder(final int maxFrameLength, final boolean stripDelimiter, final ChannelBuffer... delimiters) {
		super(maxFrameLength, stripDelimiter, delimiters);
	}

	@Override
	public synchronized void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
		if(this.framingEnabled) {
			super.messageReceived(ctx, e);
		} else {
			ctx.sendUpstream(e);
		}
	}

	public synchronized void enableFraming() {
		this.framingEnabled = true;

	}

	public synchronized void disableFraming(final ChannelHandlerContext ctx) {
		this.framingEnabled = false;
		if(this.cumulation != null && this.cumulation.readable()) {
			final ChannelBuffer spareBytes = this.cumulation.readBytes(this.cumulation.readableBytes());
			Channels.fireMessageReceived(ctx, spareBytes);
		}
	}

	@Override
	protected synchronized ChannelBuffer createCumulationDynamicBuffer(final ChannelHandlerContext ctx) {
		this.cumulation = super.createCumulationDynamicBuffer(ctx);
		return this.cumulation;
	}
}