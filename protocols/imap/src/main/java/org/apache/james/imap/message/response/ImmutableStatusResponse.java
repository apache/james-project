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

package org.apache.james.imap.message.response;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponse;

/**
 * Immutable status response. Suitable for unpooled usage.
 * 
 * @see StatusResponse
 */
public class ImmutableStatusResponse implements StatusResponse {

    private final ResponseCode responseCode;

    private final Type serverResponseType;

    private final String tag;

    private final HumanReadableText textKey;

    private final ImapCommand command;

    public ImmutableStatusResponse(final Type serverResponseType, final String tag, final ImapCommand command, final HumanReadableText textKey, final ResponseCode responseCode) {
        super();
        this.responseCode = responseCode;
        this.serverResponseType = serverResponseType;
        this.tag = tag;
        this.textKey = textKey;
        this.command = command;
    }

    /**
     * @see StatusResponse#getResponseCode()
     */
    public ResponseCode getResponseCode() {
        return responseCode;
    }

    /**
     * @see StatusResponse#getServerResponseType()
     */
    public Type getServerResponseType() {
        return serverResponseType;
    }

    /**
     * @see StatusResponse#getTag()
     */
    public String getTag() {
        return tag;
    }

    /**
     * @see StatusResponse#getTextKey()
     */
    public HumanReadableText getTextKey() {
        return textKey;
    }

    /**
     * @see StatusResponse#getCommand()
     */
    public ImapCommand getCommand() {
        return command;
    }
}
