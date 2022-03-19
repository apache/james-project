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
package org.apache.james.imap.message.request;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import jakarta.mail.Flags;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.message.request.ImapRequest;
import org.apache.james.imap.message.Literal;

import com.google.common.base.MoreObjects;

/**
 * {@link ImapRequest} which request the append of a message to a mailbox
 */
public class AppendRequest extends AbstractImapRequest implements Closeable {
    private final String mailboxName;
    private final Flags flags;
    private final Date datetime;
    private final Literal message;

    public AppendRequest(String mailboxName, Flags flags, Date datetime, Literal message, Tag tag) {
        super(tag, ImapConstants.APPEND_COMMAND);
        this.mailboxName = mailboxName;
        this.flags = flags;
        this.datetime = datetime;
        this.message = message;
    }

    /**
     * Return the Date used for the append
     * 
     * @return date
     */
    public Date getDatetime() {
        return datetime;
    }

    /**
     * Return Flags for the Message
     * 
     * @return flags
     */
    public Flags getFlags() {
        return flags;
    }

    /**
     * Return the name of the mailbox we want to append to
     * 
     * @return mailboxName
     */
    public String getMailboxName() {
        return mailboxName;
    }

    /**
     * Return the message to append as {@link InputStream}
     * 
     * @return message
     */
    public Literal getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("mailboxName", mailboxName)
            .add("flags", flags)
            .add("datetime", datetime)
            .add("message", message)
            .toString();
    }

    @Override
    public void close() throws IOException {
        if (message instanceof Closeable) {
            ((Closeable) message).close();
        }
    }
}
