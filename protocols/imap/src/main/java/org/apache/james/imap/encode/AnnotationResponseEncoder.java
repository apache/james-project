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

import com.google.common.base.Optional;
import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.encode.base.AbstractChainedImapEncoder;
import org.apache.james.imap.message.response.AnnotationResponse;
import org.apache.james.mailbox.model.MailboxAnnotation;

public class AnnotationResponseEncoder extends AbstractChainedImapEncoder {

    public AnnotationResponseEncoder(ImapEncoder next) {
        super(next);
    }

    protected void doEncode(ImapMessage acceptableMessage, final ImapResponseComposer composer, ImapSession session) throws IOException {

        AnnotationResponse response = (AnnotationResponse) acceptableMessage;

        composer.untagged();
        composer.commandName(ImapConstants.ANNOTATION_RESPONSE_NAME);

        composer.quote(Optional.fromNullable(response.getMailboxName()).or(""));
        composeAnnotations(composer, response.getMailboxAnnotations(), session);

        composer.end();
    }

    private void composeAnnotations(ImapResponseComposer composer, List<MailboxAnnotation> annotations, ImapSession session) throws IOException {
        if (!annotations.isEmpty()) {
            composer.openParen();
            for (MailboxAnnotation annotation : annotations) {
                if (annotation.isNil()) {
                    session.getLog().warn("There is nil data of key {} on store: ", annotation.getKey().getKey());
                } else {
                    composer.message(annotation.getKey().getKey());
                    composer.quote(annotation.getValue().or(""));
                }
            }
            composer.closeParen();
        }
    }

    public boolean isAcceptable(ImapMessage message) {
        return message instanceof AnnotationResponse;
    }
}
