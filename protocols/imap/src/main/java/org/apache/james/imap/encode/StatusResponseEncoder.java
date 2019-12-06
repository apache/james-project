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

package org.apache.james.imap.encode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.display.Locales;
import org.apache.james.imap.api.display.Localizer;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponse.ResponseCode;
import org.apache.james.imap.api.message.response.StatusResponse.Type;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.response.ImmutableStatusResponse;

public class StatusResponseEncoder implements ImapResponseEncoder<ImmutableStatusResponse> {
    private final Localizer localizer;

    public StatusResponseEncoder(Localizer localizer) {
        this.localizer = localizer;
    }

    @Override
    public Class<ImmutableStatusResponse> acceptableMessages() {
        return ImmutableStatusResponse.class;
    }

    @Override
    public void encode(ImmutableStatusResponse response, ImapResponseComposer composer, ImapSession session) throws IOException {
        final Type serverResponseType = response.getServerResponseType();
        final String type = asString(serverResponseType);
        final ResponseCode responseCode = response.getResponseCode();
        final String code = asString(responseCode);
        final Tag tag = response.getTag();
        final ImapCommand command = response.getCommand();
        final HumanReadableText textKey = response.getTextKey();
        final String text = asString(textKey, session);
        final Collection<String> parameters;
        final long number;
        final boolean useParens;
        if (responseCode == null) {
            parameters = null;
            number = -1;
            useParens = false;
        } else {
            parameters = responseCode.getParameters();
            number = responseCode.getNumber();
            useParens = responseCode.useParens();
        }
        //composer.statusResponse(tag, command, type, code, parameters, useParens, number, text);
        
        if (tag == null) {
            composer.untagged();
        } else {
            composer.tag(tag);
        }
        composer.message(type);
        if (responseCode != null) {
            composer.openSquareBracket();
            composer.message(code);
            if (number > -1) {
                composer.message(number);
            }
            if (parameters != null && !parameters.isEmpty()) {
                if (useParens) {
                    composer.openParen();
                }
                for (String parameter : parameters) {
                    composer.message(parameter);
                }
                if (useParens) {
                    composer.closeParen();
                }
            }
            composer.closeSquareBracket();
        }
        if (command != null) {
            composer.commandName(command.getName());
        }
        if (text != null && !"".equals(text)) {
            composer.message(text);
        }
        composer.end();
    }

    private String asString(HumanReadableText text, ImapSession session) {
        // TODO: calculate locales
        return localizer.localize(text, new Locales(new ArrayList<>(), null));
    }

    private String asString(StatusResponse.ResponseCode code) {
        final String result;
        if (code == null) {
            result = null;
        } else {
            result = code.getCode();
        }
        return result;
    }

    private String asString(StatusResponse.Type type) {
        final String result;
        if (type == null) {
            result = null;
        } else {
            result = type.getCode();
        }
        return result;
    }
}
