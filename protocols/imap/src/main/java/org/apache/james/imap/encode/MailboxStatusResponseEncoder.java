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
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.encode.base.AbstractChainedImapEncoder;
import org.apache.james.imap.message.response.MailboxStatusResponse;

/**
 * Encodes <code>STATUS</code> responses.
 */
public class MailboxStatusResponseEncoder extends AbstractChainedImapEncoder implements ImapConstants {

    public MailboxStatusResponseEncoder(ImapEncoder next) {
        super(next);
    }

    protected void doEncode(ImapMessage acceptableMessage, ImapResponseComposer composer, ImapSession session) throws IOException {
        MailboxStatusResponse response = (MailboxStatusResponse) acceptableMessage;
        Long messages = response.getMessages();
        Long recent = response.getRecent();
        Long uidNext = response.getUidNext();
        Long highestModSeq = response.getHighestModSeq();
        Long uidValidity = response.getUidValidity();
        Long unseen = response.getUnseen();
        String mailboxName = response.getMailbox();

        composer.untagged();
        composer.message(STATUS_COMMAND_NAME);
        composer.quote(mailboxName);
        composer.openParen();

        if (messages != null) {
        	composer.message(STATUS_MESSAGES);
            final long messagesValue = messages.longValue();
            composer.message(messagesValue);
        }

        if (recent != null) {
        	composer.message(STATUS_RECENT);
            final long recentValue = recent.longValue();
            composer.message(recentValue);
        }

        if (uidNext != null) {
        	composer.message(STATUS_UIDNEXT);
            final long uidNextValue = uidNext.longValue();
            composer.message(uidNextValue);
        }
        
        if (highestModSeq != null) {
        	composer.message(STATUS_HIGHESTMODSEQ);
        	composer.message(highestModSeq);
        }

        if (uidValidity != null) {
        	composer.message(STATUS_UIDVALIDITY);
            final long uidValidityValue = uidValidity.longValue();
            composer.message(uidValidityValue);
        }

        if (unseen != null) {
        	composer.message(STATUS_UNSEEN);
            final long unseenValue = unseen.longValue();
            composer.message(unseenValue);
        }

        composer.closeParen();
        composer.end();
    }

    protected boolean isAcceptable(ImapMessage message) {
        return message != null && message instanceof MailboxStatusResponse;
    }

}
