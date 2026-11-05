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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.message.BytesBackedLiteral;
import org.apache.james.imap.message.response.MetadataResponse;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.CharMatcher;

public class MetadataResponseEncoder implements ImapResponseEncoder<MetadataResponse> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataResponseEncoder.class);
    private static final CharMatcher LINE_BREAK = CharMatcher.anyOf("\r\n");

    @Override
    public Class<MetadataResponse> acceptableMessages() {
        return MetadataResponse.class;
    }

    @Override
    public void encode(MetadataResponse response, ImapResponseComposer composer) throws IOException {
        composer.untagged();
        composer.message(ImapConstants.ANNOTATION_RESPONSE_NAME);

        composer.mailbox(Optional.ofNullable(response.getMailboxName()).orElse(""));
        composeAnnotations(composer, response.getMailboxAnnotations());

        composer.end();
    }

    private void composeAnnotations(ImapResponseComposer composer, List<MailboxAnnotation> annotations) throws IOException {
        if (!annotations.isEmpty()) {
            composer.openParen();
            for (MailboxAnnotation annotation : annotations) {
                composeAnnotation(composer, annotation);
            }
            composer.closeParen();
        }
    }

    private void composeAnnotation(ImapResponseComposer composer, MailboxAnnotation annotation) throws IOException {
        if (annotation.isNil()) {
            LOGGER.warn("There is nil data of key {} on store: ", annotation.getKey().asString());
        } else {
            composer.message(annotation.getKey().asString());
            composeValue(composer, annotation.getValue().orElse(""));
        }
    }

    // RFC 5464: a value may hold several lines, which a quoted string can not carry. A NUL fits in neither of them:
    // SETMETADATA refuses it, values stored before that get it replaced, by ImapResponseComposer#quote otherwise.
    private void composeValue(ImapResponseComposer composer, String value) throws IOException {
        if (LINE_BREAK.matchesAnyOf(value)) {
            composer.literal(BytesBackedLiteral.of(value.replace('\0', ' ').getBytes(StandardCharsets.UTF_8)));
        } else {
            composer.quote(value);
        }
    }
}
