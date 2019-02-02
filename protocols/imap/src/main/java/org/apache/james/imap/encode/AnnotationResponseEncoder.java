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
import java.util.List;
import java.util.Optional;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.encode.base.AbstractChainedImapEncoder;
import org.apache.james.imap.message.response.AnnotationResponse;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnnotationResponseEncoder extends AbstractChainedImapEncoder {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationResponseEncoder.class);

    public AnnotationResponseEncoder(ImapEncoder next) {
        super(next);
    }

    @Override
    protected void doEncode(ImapMessage acceptableMessage, final ImapResponseComposer composer, ImapSession session) throws IOException {

        AnnotationResponse response = (AnnotationResponse) acceptableMessage;

        composer.untagged();
        composer.commandName(ImapConstants.ANNOTATION_RESPONSE_NAME);

        composer.quote(Optional.ofNullable(response.getMailboxName()).orElse(""));
        composeAnnotations(composer, session, response.getMailboxAnnotations());

        composer.end();
    }

    private void composeAnnotations(ImapResponseComposer composer, ImapSession session, List<MailboxAnnotation> annotations) throws IOException {
        if (!annotations.isEmpty()) {
            composer.openParen();
            for (MailboxAnnotation annotation : annotations) {
                composeAnnotation(composer, session, annotation);
            }
            composer.closeParen();
        }
    }

    private void composeAnnotation(ImapResponseComposer composer, ImapSession session, MailboxAnnotation annotation) throws IOException {
        if (annotation.isNil()) {
            LOGGER.warn("There is nil data of key {} on store: ", annotation.getKey().asString());
        } else {
            composer.message(annotation.getKey().asString());
            composer.quote(annotation.getValue().orElse(""));
        }
    }

    @Override
    public boolean isAcceptable(ImapMessage message) {
        return message instanceof AnnotationResponse;
    }
}
