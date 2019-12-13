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

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.base.AbstractImapCommandParser;
import org.apache.james.imap.message.request.GetAnnotationRequest;
import org.apache.james.imap.message.request.GetAnnotationRequest.Depth;
import org.apache.james.mailbox.model.MailboxAnnotationKey;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;

public class GetAnnotationCommandParser extends AbstractImapCommandParser {
    private static final CharMatcher ENDOFLINE_PATTERN = CharMatcher.isNot('\n').and(CharMatcher.isNot('\r'));
    private static final  String MAXSIZE = "MAXSIZE";
    private static final String DEPTH = "DEPTH";
    private static final boolean STOP_ON_PAREN = true;

    public GetAnnotationCommandParser(StatusResponseFactory statusResponseFactory) {
        super(ImapConstants.GETANNOTATION_COMMAND, statusResponseFactory);
    }

    @Override
    protected ImapMessage decode(ImapRequestLineReader requestReader, Tag tag, ImapSession session)
        throws DecodingException {
        try {
            return buildAnnotationRequest(requestReader, tag);
        } catch (NullPointerException | IllegalArgumentException | IllegalStateException e) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, e.getMessage(), e);
        }
    }

    private ImapMessage buildAnnotationRequest(ImapRequestLineReader requestReader, Tag tag) throws DecodingException {
        GetAnnotationRequest.Builder builder = GetAnnotationRequest.builder().tag(tag);
        builder.mailboxName(requestReader.mailbox());

        consumeOptionsAndKeys(requestReader, builder);

        if (ENDOFLINE_PATTERN.matches(requestReader.nextNonSpaceChar())) {
            consumeKey(requestReader, builder);
        }

        return builder.build();
    }

    private void consumeOptionsAndKeys(ImapRequestLineReader requestReader, GetAnnotationRequest.Builder builder) throws DecodingException {
        while (requestReader.nextNonSpaceChar() == '(') {
            requestReader.consumeChar('(');
            switch (requestReader.nextChar()) {
                case 'M':
                    consumeMaxsizeOpt(requestReader, builder);
                    break;

                case 'D':
                    consumeDepthOpt(requestReader, builder);
                    break;

                default:
                    consumeKeys(requestReader, builder);
                    break;
            }
        }
    }

    private void consumeDepthOpt(ImapRequestLineReader requestReader, GetAnnotationRequest.Builder builder) throws DecodingException {
        if (!requestReader.atom().equalsIgnoreCase(DEPTH)) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Wrong on, it should be DEPTH");
        }

        builder.depth(Depth.fromString(requestReader.atom()));
        requestReader.consumeChar(')');
    }

    private void consumeMaxsizeOpt(ImapRequestLineReader requestReader, GetAnnotationRequest.Builder builder) throws DecodingException {
        if (!requestReader.atom().equalsIgnoreCase(MAXSIZE)) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Wrong on, it should be MAXSIZE");
        }

        builder.maxsize(Optional.of((int)requestReader.number(STOP_ON_PAREN)));
        requestReader.consumeChar(')');
    }

    private void consumeKey(ImapRequestLineReader requestReader, GetAnnotationRequest.Builder builder) throws DecodingException {
        builder.keys(ImmutableSet.of(new MailboxAnnotationKey(requestReader.atom())));
        requestReader.eol();
    }

    private void consumeKeys(ImapRequestLineReader requestReader, GetAnnotationRequest.Builder builder) throws DecodingException {
        Builder<MailboxAnnotationKey> keys = ImmutableSet.builder();

        do {
            keys.add(new MailboxAnnotationKey(requestReader.atom()));
        } while (requestReader.nextWordChar() != ')');

        builder.keys(keys.build());

        requestReader.consumeChar(')');
        requestReader.eol();
    }
}
