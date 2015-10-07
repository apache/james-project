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
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.message.response.StatusResponse.ResponseCode;

public abstract class AbstactStatusResponseFactory implements StatusResponseFactory {

    public AbstactStatusResponseFactory() {
        super();
    }

    protected abstract StatusResponse createResponse(StatusResponse.Type type, String tag, ImapCommand command, HumanReadableText displayTextKey, ResponseCode code);

    public StatusResponse bye(HumanReadableText displayTextKey, ResponseCode code) {
        return createResponse(StatusResponse.Type.BYE, null, null, displayTextKey, code);
    }

    public StatusResponse bye(HumanReadableText displayTextKey) {
        return bye(displayTextKey, null);
    }

    public StatusResponse preauth(HumanReadableText displayTextKey, ResponseCode code) {
        return createResponse(StatusResponse.Type.PREAUTH, null, null, displayTextKey, code);
    }

    public StatusResponse preauth(HumanReadableText displayTextKey) {
        return preauth(displayTextKey, null);
    }

    public StatusResponse taggedBad(String tag, ImapCommand command, HumanReadableText displayTextKey, ResponseCode code) {
        return createResponse(StatusResponse.Type.BAD, tag, command, displayTextKey, code);
    }

    public StatusResponse taggedBad(String tag, ImapCommand command, HumanReadableText displayTextKey) {
        return taggedBad(tag, command, displayTextKey, null);
    }

    public StatusResponse taggedNo(String tag, ImapCommand command, HumanReadableText displayTextKey, ResponseCode code) {
        return createResponse(StatusResponse.Type.NO, tag, command, displayTextKey, code);
    }

    public StatusResponse taggedNo(String tag, ImapCommand command, HumanReadableText displayTextKey) {
        return taggedNo(tag, command, displayTextKey, null);
    }

    public StatusResponse taggedOk(String tag, ImapCommand command, HumanReadableText displayTextKey, ResponseCode code) {
        return createResponse(StatusResponse.Type.OK, tag, command, displayTextKey, code);
    }

    public StatusResponse taggedOk(String tag, ImapCommand command, HumanReadableText displayTextKey) {
        return taggedOk(tag, command, displayTextKey, null);
    }

    public StatusResponse untaggedBad(HumanReadableText displayTextKey, ResponseCode code) {
        return taggedBad(null, null, displayTextKey, code);
    }

    public StatusResponse untaggedBad(HumanReadableText displayTextKey) {
        return untaggedBad(displayTextKey, null);
    }

    public StatusResponse untaggedNo(HumanReadableText displayTextKey, ResponseCode code) {
        return taggedNo(null, null, displayTextKey, code);
    }

    public StatusResponse untaggedNo(HumanReadableText displayTextKey) {
        return untaggedNo(displayTextKey, null);
    }

    public StatusResponse untaggedOk(HumanReadableText displayTextKey, ResponseCode code) {
        return taggedOk(null, null, displayTextKey, code);
    }

    public StatusResponse untaggedOk(HumanReadableText displayTextKey) {
        return untaggedOk(displayTextKey, null);
    }
}