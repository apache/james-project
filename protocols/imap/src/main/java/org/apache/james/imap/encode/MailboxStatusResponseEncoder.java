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

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.response.MailboxStatusResponse;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;

/**
 * Encodes <code>STATUS</code> responses.
 */
public class MailboxStatusResponseEncoder implements ImapConstants, ImapResponseEncoder<MailboxStatusResponse> {
    @Override
    public Class<MailboxStatusResponse> acceptableMessages() {
        return MailboxStatusResponse.class;
    }

    @Override
    public void encode(MailboxStatusResponse response, ImapResponseComposer composer, ImapSession session) throws IOException {
        Long messages = response.getMessages();
        Long recent = response.getRecent();
        MessageUid uidNext = response.getUidNext();
        ModSeq highestModSeq = response.getHighestModSeq();
        Long uidValidity = response.getUidValidity();
        Long unseen = response.getUnseen();
        String mailboxName = response.getMailbox();

        composer.untagged();
        composer.message(STATUS_COMMAND_NAME);
        composer.quote(mailboxName);
        composer.openParen();

        if (messages != null) {
            composer.message(STATUS_MESSAGES);
            final long messagesValue = messages;
            composer.message(messagesValue);
        }

        if (recent != null) {
            composer.message(STATUS_RECENT);
            final long recentValue = recent;
            composer.message(recentValue);
        }

        if (uidNext != null) {
            composer.message(STATUS_UIDNEXT);
            final long uidNextValue = uidNext.asLong();
            composer.message(uidNextValue);
        }
        
        if (highestModSeq != null) {
            composer.message(STATUS_HIGHESTMODSEQ);
            composer.message(highestModSeq.asLong());
        }

        if (uidValidity != null) {
            composer.message(STATUS_UIDVALIDITY);
            final long uidValidityValue = uidValidity;
            composer.message(uidValidityValue);
        }

        if (unseen != null) {
            composer.message(STATUS_UNSEEN);
            final long unseenValue = unseen;
            composer.message(unseenValue);
        }

        composer.closeParen();
        composer.end();
    }
}
