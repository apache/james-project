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
package org.apache.james.imap.decode.parser;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.base.AbstractImapCommandParser;
import org.apache.james.imap.message.request.IDRequest;

import com.google.common.collect.ImmutableMap;

/**
 * Parses ID commands
 *
 * CF https://www.rfc-editor.org/rfc/rfc2971.html
 */
public class IDCommandParser extends AbstractImapCommandParser {

    @Inject
    public IDCommandParser(StatusResponseFactory statusResponseFactory) {
        super(ImapConstants.ID_COMMAND, statusResponseFactory);
    }

    @Override
    protected ImapMessage decode(ImapRequestLineReader request, Tag tag, ImapSession session) throws DecodingException {
        char c = request.nextWordChar();
        if (c != '(') {
            String s = request.consumeWord(ImapRequestLineReader.NoopCharValidator.INSTANCE);
            if (s.equalsIgnoreCase("NIL")) {
                request.eol();
                return new IDRequest(tag, Optional.empty());
            }
        }
        request.consumeChar('(');

        ImmutableMap.Builder<String, String> parameters = ImmutableMap.builder();
        boolean first = true;

        while (c != ')') {
            if (first) {
                first = false;
            } else {
                if (request.nextWordChar() == ',') {
                    request.consumeChar(',');
                }
            }
            request.nextWordChar();
            String field = request.consumeQuoted();
            if (request.nextWordChar() == ',') {
                request.consumeChar(',');
            }
            request.nextWordChar();
            String value = request.consumeQuoted();

            parameters.put(field, value);

            c = request.nextWordChar();
        }

        request.consumeChar(')');
        request.eol();
        return new IDRequest(tag, Optional.of(parameters.build()));
    }
}
