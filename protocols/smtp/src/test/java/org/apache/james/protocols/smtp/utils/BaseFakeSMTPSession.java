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
import java.util.Optional;

import org.apache.james.core.Username;
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

    @Override
    public Map<AttachmentKey<?>, Object> getConnectionState() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    @Override
    public int getRcptCount() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    @Override
    public String getSessionID() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    @Override
    public Map<AttachmentKey<?>, Object> getState() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    @Override
    public Username getUsername() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    @Override
    public boolean isAuthSupported() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    @Override
    public boolean isRelayingAllowed() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    @Override
    public void resetState() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    @Override
    public void setRelayingAllowed(boolean relayingAllowed) {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    @Override
    public void setUsername(Username username) {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    @Override
    public void popLineHandler() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    @Override
    public boolean isStartTLSSupported() {
        return false;
    }

    @Override
    public boolean isTLSStarted() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    @Override
    public int getPushedLineHandlerCount() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    @Override
    public Response newLineTooLongResponse() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    @Override
    public Response newFatalErrorResponse() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return new InetSocketAddress("localhost", 22);
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    @Override
    public SMTPConfiguration getConfiguration() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    @Override
    public <T> Optional<T> setAttachment(AttachmentKey<T> key, T value, State state) {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    @Override
    public <T> Optional<T> removeAttachment(AttachmentKey<T> key, State state) {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    @Override
    public <T> Optional<T> getAttachment(AttachmentKey<T> key, State state) {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    @Override
    public Charset getCharset() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    @Override
    public String getLineDelimiter() {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

    @Override
    public <T extends ProtocolSession> void pushLineHandler(LineHandler<T> overrideCommandHandler) {
        throw new UnsupportedOperationException("Unimplemented Stub Method");
    }

}
