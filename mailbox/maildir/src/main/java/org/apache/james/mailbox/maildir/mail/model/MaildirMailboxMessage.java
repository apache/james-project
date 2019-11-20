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
package org.apache.james.mailbox.maildir.mail.model;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.maildir.MaildirFolder;
import org.apache.james.mailbox.maildir.MaildirId;
import org.apache.james.mailbox.maildir.MaildirMessageName;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.DelegatingMailboxMessage;

public class MaildirMailboxMessage extends DelegatingMailboxMessage {

    private boolean answered;
    private boolean deleted;
    private boolean draft;
    private boolean flagged;
    private boolean recent;
    private boolean seen;
    private final Mailbox mailbox;
    private MessageUid uid;
    protected boolean newMessage;
    private ModSeq modSeq;
    
    public MaildirMailboxMessage(Mailbox mailbox, MessageUid messageUid, MaildirMessageName messageName) throws IOException {
        super(new MaildirMessage(messageName));

        this.mailbox = mailbox;
        setUid(messageUid);
        setModSeq(ModSeq.of(messageName.getFile().lastModified()));
        Flags flags = messageName.getFlags();
        
        // Set the flags for the message and respect if its RECENT
        // See MAILBOX-84
        File file = messageName.getFile();
        if (!file.exists()) {
            throw new FileNotFoundException("Unable to read file " + file.getAbsolutePath() + " for the message");
        } else {
            // if the message resist in the new folder its RECENT
            if (file.getParentFile().getName().equals(MaildirFolder.NEW)) {
                if (flags == null) {
                    flags = new Flags();
                }
                flags.add(Flags.Flag.RECENT);
            }
        }
        setFlags(flags);
    }

    @Override
    public ComposedMessageIdWithMetaData getComposedMessageIdWithMetaData() {
        return ComposedMessageIdWithMetaData.builder()
            .modSeq(modSeq)
            .flags(createFlags())
            .composedMessageId(new ComposedMessageId(mailbox.getMailboxId(), getMessageId(), uid))
            .build();
    }

    
    @Override
    public MaildirId getMailboxId() {
        return (MaildirId) mailbox.getMailboxId();
    }

    @Override
    public MessageUid getUid() {
        return uid;
    }

    @Override
    public void setUid(MessageUid uid) {
        this.uid = uid;
    }


    @Override
    public void setFlags(Flags flags) {
        if (flags != null) {
            answered = flags.contains(Flags.Flag.ANSWERED);
            deleted = flags.contains(Flags.Flag.DELETED);
            draft = flags.contains(Flags.Flag.DRAFT);
            flagged = flags.contains(Flags.Flag.FLAGGED);
            recent = flags.contains(Flags.Flag.RECENT);
            seen = flags.contains(Flags.Flag.SEEN);
        }
    }
    
    @Override
    public boolean isAnswered() {
        return answered;
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public boolean isDraft() {
        return draft;
    }

    @Override
    public boolean isFlagged() {
        return flagged;
    }

    @Override
    public boolean isRecent() {
        return recent;
    }

    @Override
    public boolean isSeen() {
        return seen;
    }

    /**
     * Indicates whether this MaildirMailboxMessage reflects a new message or one that already
     * exists in the file system.
     * @return true if it is new, false if it already exists
     */
    public boolean isNew() {
        return newMessage;
    }

    @Override
    public ModSeq getModSeq() {
        return modSeq;
    }

    @Override
    public void setModSeq(ModSeq modSeq) {
        this.modSeq = modSeq;
    }

    @Override
    public String toString() {
        StringBuilder theString = new StringBuilder("MaildirMailboxMessage ");
        theString.append(getUid());
        theString.append(" {");
        Flags flags = createFlags();
        if (flags.contains(Flags.Flag.DRAFT)) {
            theString.append(MaildirMessageName.FLAG_DRAFT);
        }
        if (flags.contains(Flags.Flag.FLAGGED)) {
            theString.append(MaildirMessageName.FLAG_FLAGGED);
        }
        if (flags.contains(Flags.Flag.ANSWERED)) {
            theString.append(MaildirMessageName.FLAG_ANSWERD);
        }
        if (flags.contains(Flags.Flag.SEEN)) {
            theString.append(MaildirMessageName.FLAG_SEEN);
        }
        if (flags.contains(Flags.Flag.DELETED)) {
            theString.append(MaildirMessageName.FLAG_DELETED);
        }
        theString.append("} ");
        theString.append(getInternalDate());
        return theString.toString();
    }

}
