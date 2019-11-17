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

import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.james.imap.api.ImapSessionState;
import org.apache.james.imap.api.process.ImapLineHandler;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.compression.ZlibDecoder;
import org.jboss.netty.handler.codec.compression.ZlibEncoder;
import org.jboss.netty.handler.codec.compression.ZlibWrapper;
import org.jboss.netty.handler.ssl.SslHandler;

public class NettyImapSession implements ImapSession, NettyConstants {
    private ImapSessionState state = ImapSessionState.NON_AUTHENTICATED;
    private SelectedMailbox selectedMailbox;
    private final Map<String, Object> attributesByKey = new HashMap<>();
    private final SSLContext sslContext;
    private final String[] enabledCipherSuites;
    private final boolean compress;
    private final Channel channel;
    private int handlerCount;
    private final boolean plainAuthDisallowed;

    public NettyImapSession(Channel channel, SSLContext sslContext, String[] enabledCipherSuites, boolean compress, boolean plainAuthDisallowed) {
        this.channel = channel;
        this.sslContext = sslContext;
        this.enabledCipherSuites = enabledCipherSuites;
        this.compress = compress;
        this.plainAuthDisallowed = plainAuthDisallowed;
    }

    @Override
    public void logout() {
        closeMailbox();
        state = ImapSessionState.LOGOUT;
    }

    @Override
    public void authenticated() {
        this.state = ImapSessionState.AUTHENTICATED;
    }

    @Override
    public void deselect() {
        this.state = ImapSessionState.AUTHENTICATED;
        closeMailbox();
    }

    @Override
    public void selected(SelectedMailbox mailbox) {
        this.state = ImapSessionState.SELECTED;
        closeMailbox();
        this.selectedMailbox = mailbox;
    }

    @Override
    public SelectedMailbox getSelected() {
        return this.selectedMailbox;
    }

    @Override
    public ImapSessionState getState() {
        return this.state;
    }

    private void closeMailbox() {
        if (selectedMailbox != null) {
            selectedMailbox.deselect();
            selectedMailbox = null;
        }
    }

    @Override
    public Object getAttribute(String key) {
        return attributesByKey.get(key);
    }

    @Override
    public void setAttribute(String key, Object value) {
        if (value == null) {
            attributesByKey.remove(key);
        } else {
            attributesByKey.put(key, value);
        }
    }

    @Override
    public boolean startTLS() {
        if (!supportStartTLS()) {
            return false;
        }
        channel.setReadable(false);

        SslHandler filter = new SslHandler(sslContext.createSSLEngine(), false);
        filter.getEngine().setUseClientMode(false);
        if (enabledCipherSuites != null && enabledCipherSuites.length > 0) {
            filter.getEngine().setEnabledCipherSuites(enabledCipherSuites);
        }
        channel.getPipeline().addFirst(SSL_HANDLER, filter);

        channel.setReadable(true);

        return true;
    }

    @Override
    public boolean supportStartTLS() {
        return sslContext != null;
    }

    @Override
    public boolean isCompressionSupported() {
        return compress;
    }

    @Override
    public boolean startCompression() {
        if (!isCompressionSupported()) {
            return false;
        }

        channel.setReadable(false);
        ZlibDecoder decoder = new ZlibDecoder(ZlibWrapper.NONE);
        ZlibEncoder encoder = new ZlibEncoder(ZlibWrapper.NONE, 5);

        // Check if we have the SslHandler in the pipeline already
        // if so we need to move the compress encoder and decoder
        // behind it in the chain
        // See JAMES-1186
        if (channel.getPipeline().get(SSL_HANDLER) == null) {
            channel.getPipeline().addFirst(ZLIB_DECODER, decoder);
            channel.getPipeline().addFirst(ZLIB_ENCODER, encoder);
        } else {
            channel.getPipeline().addAfter(SSL_HANDLER, ZLIB_DECODER, decoder);
            channel.getPipeline().addAfter(SSL_HANDLER, ZLIB_ENCODER, encoder);
        }

        channel.setReadable(true);

        return true;
    }

    @Override
    public void pushLineHandler(ImapLineHandler lineHandler) {
        channel.setReadable(false);
        channel.getPipeline().addBefore(REQUEST_DECODER, "lineHandler" + handlerCount++, new ImapLineHandlerAdapter(this, lineHandler));
        channel.setReadable(true);
    }

    @Override
    public void popLineHandler() {
        channel.setReadable(false);
        channel.getPipeline().remove("lineHandler" + --handlerCount);
        channel.setReadable(true);
    }

    @Override
    public boolean isPlainAuthDisallowed() {
        return plainAuthDisallowed;
    }

    @Override
    public boolean isTLSActive() {
        return channel.getPipeline().get(SSL_HANDLER) != null;
    }

    @Override
    public boolean supportMultipleNamespaces() {
        return false;
    }

    @Override
    public boolean isCompressionActive() {
        return channel.getPipeline().get(ZLIB_DECODER) != null;
    }

}
