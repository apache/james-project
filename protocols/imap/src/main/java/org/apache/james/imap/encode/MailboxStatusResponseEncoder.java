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
import java.util.Optional;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.message.response.MailboxStatusResponse;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.UidValidity;

/**
 * Encodes <code>STATUS</code> responses.
 */
public class MailboxStatusResponseEncoder implements ImapConstants, ImapResponseEncoder<MailboxStatusResponse> {
    @Override
    public Class<MailboxStatusResponse> acceptableMessages() {
        return MailboxStatusResponse.class;
    }

    @Override
    public void encode(MailboxStatusResponse response, ImapResponseComposer composer) throws IOException {
        Long messages = response.getMessages();
        Long recent = response.getRecent();
        Long size = response.getSize();
        Long deleted = response.getDeleted();
        Long deletedStorage = response.getDeletedStorage();
        Optional<Long> appendLimit = response.getAppendLimit();
        MessageUid uidNext = response.getUidNext();
        ModSeq highestModSeq = response.getHighestModSeq();
        UidValidity uidValidity = response.getUidValidity();
        Long unseen = response.getUnseen();
        MailboxId mailboxId = response.getMailboxId();
        String mailboxName = response.getMailbox();

        composer.untagged();
        composer.message(STATUS_COMMAND.getNameAsBytes());
        composer.mailbox(mailboxName);
        composer.openParen();

        if (appendLimit != null) {
            composer.message(STATUS_APPENDLIMIT);
            composer.message(appendLimit.map(l -> Long.toString(l)).orElse("NIL"));
        }

        if (messages != null) {
            composer.message(STATUS_MESSAGES);
            composer.message(messages);
        }

        if (size != null) {
            composer.message(STATUS_SIZE);
            composer.message(size);
        }

        if (deleted != null) {
            composer.message(STATUS_DELETED);
            composer.message(deleted);
        }

        if (deletedStorage != null) {
            composer.message(STATUS_DELETED_STORAGE);
            composer.message(deletedStorage);
        }

        if (recent != null) {
            composer.message(STATUS_RECENT);
            composer.message(recent);
        }

        if (uidNext != null) {
            composer.message(STATUS_UIDNEXT);
            composer.message(uidNext.asLong());
        }
        
        if (highestModSeq != null) {
            composer.message(STATUS_HIGHESTMODSEQ);
            composer.message(highestModSeq.asLong());
        }

        if (uidValidity != null) {
            composer.message(STATUS_UIDVALIDITY);
            composer.message(uidValidity.asLong());
        }

        if (unseen != null) {
            composer.message(STATUS_UNSEEN);
            composer.message(unseen);
        }

        if (mailboxId != null) {
            composer.message(STATUS_MAILBOXID);
            composer.openParen();
            composer.message(mailboxId.serialize());
            composer.closeParen();
        }

        composer.closeParen();
        composer.end();
    }
}
