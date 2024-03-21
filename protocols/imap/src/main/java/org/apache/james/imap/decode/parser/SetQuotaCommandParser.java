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

import jakarta.inject.Inject;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.base.AbstractImapCommandParser;
import org.apache.james.imap.message.request.SetQuotaRequest;

/**
 * SETQUOTA command parser
 */
public class SetQuotaCommandParser extends AbstractImapCommandParser {

    @Inject
    public SetQuotaCommandParser(StatusResponseFactory statusResponseFactory) {
        super(ImapConstants.SETQUOTA_COMMAND, statusResponseFactory);
    }

    @Override
    protected ImapMessage decode(ImapRequestLineReader request, Tag tag, ImapSession session) throws DecodingException {
        final String quotaRoot = request.atom();
        SetQuotaRequest setQuotaRequest = new SetQuotaRequest(tag, quotaRoot);
        // We now parse resource limit declaration
        // It has the following shape : (RESOURCE1 1024000) (RESOURCE2 2048000)\n
        request.nextWordChar();
        while (request.nextChar() == '(') {
            request.consume();
            String resource = request.atom();
            request.nextWordChar();
            long limit = request.number(true);
            request.nextWordChar();
            request.consumeChar(')');
            setQuotaRequest.addResourceLimit(resource, limit);
            // Consume white spaces
            // Do not use nextWorldChar as it throws an exception on end of lines
            while (request.nextChar() == ' ') {
                request.consume();
            }
        }
        request.eol();
        return setQuotaRequest;
    }

}
