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
package org.apache.james.protocols.smtp;

import java.util.List;
import java.util.Optional;

import org.apache.james.protocols.api.ProtocolSessionImpl;
import org.apache.james.protocols.api.ProtocolTransport;
import org.apache.james.protocols.api.ProxyInformation;
import org.apache.james.protocols.api.Response;

/**
 * {@link SMTPSession} implementation
 */
public class SMTPSessionImpl extends ProtocolSessionImpl implements SMTPSession {

    private static final Response LINE_LENGTH_EXCEEDED = new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_COMMAND_UNRECOGNIZED, "Line length exceeded. See RFC 2821 #4.5.3.1.").immutable();
    private static final Response FATAL_ERROR = new SMTPResponse(SMTPRetCode.LOCAL_ERROR, "Unable to process request").immutable();
    private static final Response UNKNOWN_COMMAND_ERROR = new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_COMMAND_UNRECOGNIZED, "Unable to process request: the command is unknown").immutable();

    private long currentMessageSize = 0L;
    private boolean relayingAllowed;
    private boolean headerComplete = false;
    private boolean messageFailed = false;

    public SMTPSessionImpl(ProtocolTransport transport, SMTPConfiguration config) {
        super(transport, config);
        relayingAllowed = computeRelayingAllowed(config);
    }

    private Boolean computeRelayingAllowed(SMTPConfiguration config) {
        return Optional.ofNullable(getRemoteAddress())
            .flatMap(address -> Optional.ofNullable(address.getAddress()))
            .flatMap(address -> Optional.ofNullable(address.getHostAddress()))
            .map(config::isRelayingAllowed)
            .orElse(false);
    }

    @Override
    public boolean isRelayingAllowed() {
        return relayingAllowed;
    }

    @Override
    public void setProxyInformation(ProxyInformation proxyInformation) {
        super.setProxyInformation(proxyInformation);
        relayingAllowed = computeRelayingAllowed((SMTPConfiguration) config);
    }

    @Override
    public void resetState() {
        // remember the ehlo mode between resets
        Optional<String> currentHeloMode = getAttachment(CURRENT_HELO_MODE, State.Connection);

        getState().clear();

        // start again with the old helo mode
        currentHeloMode.ifPresent(heloMode -> setAttachment(CURRENT_HELO_MODE, heloMode, State.Connection));

        currentMessageSize = 0L;
        headerComplete = false;
        messageFailed = false;
    }

    @Override
    public int getRcptCount() {
        return getAttachment(SMTPSession.RCPT_LIST, State.Transaction)
            .map(List::size)
            .orElse(0);
    }

    @Override
    public boolean supportsOAuth() {
        return getConfiguration().saslConfiguration().isPresent() && isAuthAnnounced();
    }

    @Override
    public boolean isAuthAnnounced() {
        return getConfiguration().isAuthAnnounced(getRemoteAddress().getAddress().getHostAddress(), isTLSStarted());
    }

    @Override
    public void setRelayingAllowed(boolean relayingAllowed) {
        this.relayingAllowed = relayingAllowed;
    }

    @Override
    public Response newLineTooLongResponse() {
        return LINE_LENGTH_EXCEEDED;
    }

    @Override
    public Response newFatalErrorResponse() {
        return FATAL_ERROR;
    }

    @Override
    public Response newCommandNotFoundErrorResponse() {
        return UNKNOWN_COMMAND_ERROR;
    }

    @Override
    public SMTPConfiguration getConfiguration() {
        return (SMTPConfiguration) config;
    }

    @Override
    public long currentMessageSize() {
        return currentMessageSize;
    }

    @Override
    public void setCurrentMessageSize(long newSize) {
        currentMessageSize = newSize;
    }

    @Override
    public boolean headerComplete() {
        return headerComplete;
    }

    @Override
    public void setHeaderComplete(boolean value) {
        headerComplete = value;
    }

    @Override
    public boolean messageFailed() {
        return messageFailed;
    }

    @Override
    public void setMessageFailed(boolean value) {
        messageFailed = value;
    }
}
