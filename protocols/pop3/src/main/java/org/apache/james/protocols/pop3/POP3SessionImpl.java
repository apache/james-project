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
package org.apache.james.protocols.pop3;

import org.apache.james.protocols.api.ProtocolConfiguration;
import org.apache.james.protocols.api.ProtocolSessionImpl;
import org.apache.james.protocols.api.ProtocolTransport;
import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.pop3.mailbox.Mailbox;

/**
 * {@link POP3Session} implementation which use Netty
 */
public class POP3SessionImpl extends ProtocolSessionImpl implements POP3Session {

    private static final Response LINE_TOO_LONG = new POP3Response(POP3Response.ERR_RESPONSE, "Exceed maximal line length").immutable();
    private int handlerState;

    private Mailbox mailbox;

    
    public POP3SessionImpl(ProtocolTransport transport, ProtocolConfiguration configData) {
        super(transport, configData);
    }

    @Override
    public int getHandlerState() {
        return handlerState;
    }

    @Override
    public void setHandlerState(int handlerState) {
        this.handlerState = handlerState;
    }

    @Override
    public void resetState() {
        getState().clear();

        setHandlerState(AUTHENTICATION_READY);
    }

    @Override
    public Mailbox getUserMailbox() {
        return mailbox;
    }

    @Override
    public void setUserMailbox(Mailbox mailbox) {
        this.mailbox = mailbox;
    }

    @Override
    public Response newLineTooLongResponse() {
        return LINE_TOO_LONG;
    }

    @Override
    public Response newFatalErrorResponse() {
        return POP3Response.ERR;
    }

    @Override
    public Response newCommandNotFoundErrorResponse() {
        return POP3Response.ERR;
    }
}
