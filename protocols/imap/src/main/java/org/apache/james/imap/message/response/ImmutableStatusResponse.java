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
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponse;

import com.google.common.base.MoreObjects;

/**
 * Immutable status response. Suitable for unpooled usage.
 */
public class ImmutableStatusResponse implements StatusResponse {
    private final ResponseCode responseCode;
    private final Type serverResponseType;
    private final Tag tag;
    private final HumanReadableText textKey;
    private final ImapCommand command;

    public ImmutableStatusResponse(Type serverResponseType, Tag tag, ImapCommand command, HumanReadableText textKey, ResponseCode responseCode) {
        super();
        this.responseCode = responseCode;
        this.serverResponseType = serverResponseType;
        this.tag = tag;
        this.textKey = textKey;
        this.command = command;
    }

    @Override
    public ResponseCode getResponseCode() {
        return responseCode;
    }

    @Override
    public Type getServerResponseType() {
        return serverResponseType;
    }

    @Override
    public Tag getTag() {
        return tag;
    }

    @Override
    public HumanReadableText getTextKey() {
        return textKey;
    }

    @Override
    public ImapCommand getCommand() {
        return command;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("responseCode", responseCode)
            .add("serverResponseType", serverResponseType)
            .add("tag", tag)
            .add("textKey", textKey)
            .add("command", command)
            .toString();
    }
}
