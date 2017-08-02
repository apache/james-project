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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.base.AbstractImapCommandParser;
import org.apache.james.imap.message.request.EnableRequest;
import org.apache.james.protocols.imap.DecodingException;

public class EnableCommandParser extends AbstractImapCommandParser {
    
    public EnableCommandParser() {
        super(ImapCommand.authenticatedStateCommand(ImapConstants.ENABLE_COMMAND_NAME));
    }

    @Override
    protected ImapMessage decode(ImapCommand command, ImapRequestLineReader request, String tag, ImapSession session) throws DecodingException {
        List<String> caps = new ArrayList<>();
        String cap = request.astring();
        caps.add(cap.toUpperCase(Locale.US));
        while (request.nextChar() == ' ') {
            request.consume();
            cap = request.astring();
            caps.add(cap.toUpperCase(Locale.US));
        }
        request.eol();
        return new EnableRequest(tag, command, caps);
    }

}
