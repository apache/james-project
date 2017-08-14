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


package org.apache.james.protocols.smtp.utils;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.Map;

import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.handler.LineHandler;
import org.apache.james.protocols.smtp.SMTPConfiguration;
import org.apache.james.protocols.smtp.SMTPSession;

/**
 * Abstract class to simplify the mocks
 */
public class BaseFakeSMTPSession implements SMTPSession {

    @Override
    public boolean needsCommandInjectionDetection() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    @Override
    public void startDetectingCommadInjection() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    @Override
    public void stopDetectingCommandInjection() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#getConnectionState()
     */
    public Map<String, Object> getConnectionState() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#getRcptCount()
     */
    public int getRcptCount() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#getSessionID()
     */
    public String getSessionID() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#getState()
     */
    public Map<String, Object> getState() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#getUser()
     */
    public String getUser() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#isAuthSupported()
     */
    public boolean isAuthSupported() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#isRelayingAllowed()
     */
    public boolean isRelayingAllowed() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#resetState()
     */
    public void resetState() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#setRelayingAllowed(boolean)
     */
    public void setRelayingAllowed(boolean relayingAllowed) {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#setUser(java.lang.String)
     */
    public void setUser(String user) {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }


    /**
     * @see org.apache.james.protocols.smtp.SMTPSession#popLineHandler()
     */
    public void popLineHandler() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    public boolean isStartTLSSupported() {
        return false;
    }

    public boolean isTLSStarted() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    public int getPushedLineHandlerCount() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    public Response newLineTooLongResponse() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    public Response newFatalErrorResponse() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.ProtocolSession#getRemoteAddress()
     */
    public InetSocketAddress getRemoteAddress() {
        return new InetSocketAddress("localhost", 22);
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.ProtocolSession#getLocalAddress()
     */
    public InetSocketAddress getLocalAddress() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.smtp.SMTPSession#getConfiguration()
     */
    public SMTPConfiguration getConfiguration() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    public Object setAttachment(String key, Object value, State state) {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    public Object getAttachment(String key, State state) {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    public Charset getCharset() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    public String getLineDelimiter() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    public <T extends ProtocolSession> void pushLineHandler(LineHandler<T> overrideCommandHandler) {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

}
