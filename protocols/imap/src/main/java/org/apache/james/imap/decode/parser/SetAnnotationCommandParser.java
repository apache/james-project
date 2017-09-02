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
import java.util.function.Function;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.base.AbstractImapCommandParser;
import org.apache.james.imap.message.request.SetAnnotationRequest;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.protocols.imap.DecodingException;

import com.google.common.collect.ImmutableList;

public class SetAnnotationCommandParser extends AbstractImapCommandParser {
    public SetAnnotationCommandParser() {
        super(ImapCommand.authenticatedStateCommand(ImapConstants.SETANNOTATION_COMMAND_NAME));
    }

    @Override
    protected ImapMessage decode(ImapCommand command, ImapRequestLineReader request, String tag, ImapSession session)
            throws DecodingException {
        String mailboxName = request.mailbox();
        ImmutableList.Builder<MailboxAnnotation> listMailboxAnnotations = ImmutableList.<MailboxAnnotation>builder();

        if (request.nextWordChar() == '(') {
            request.consumeChar('(');

            do {
                listMailboxAnnotations.add(readNextAnnotation(request));
            } while (request.nextWordChar() != ')');

            request.consumeChar(')');
        }
        request.eol();

        return new SetAnnotationRequest(tag, command, mailboxName, listMailboxAnnotations.build());
    }

    private MailboxAnnotation readNextAnnotation(ImapRequestLineReader request) throws DecodingException {
        try {
            String key = request.atom();
            String value = request.nstring();

            return Optional.ofNullable(value)
                .map(transforMailboxAnnotation(key))
                .orElse(MailboxAnnotation.nil(createAnnotationKey(key)));
        } catch (IllegalArgumentException e) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "The key is not valid: " + e.getMessage());
        }
    }

    private Function<String, MailboxAnnotation> transforMailboxAnnotation(final String key) {
        return value -> MailboxAnnotation.newInstance(createAnnotationKey(key), value);
    }

    private MailboxAnnotationKey createAnnotationKey(String key) {
        return new MailboxAnnotationKey(key);
    }

}
