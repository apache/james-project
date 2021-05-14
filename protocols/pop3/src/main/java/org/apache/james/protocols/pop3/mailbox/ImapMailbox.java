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
package org.apache.james.protocols.pop3.mailbox;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * An IMAP Mailbox adapter which is used in POP3 to retrieve messages
 */
@Deprecated
public abstract class ImapMailbox implements Mailbox {

    /**
     * Returns the message body as {@link InputStream} or <code>null</code> if
     * no message can be found for the given <code>uid</code>
     */
    public abstract InputStream getMessageBody(long uid) throws IOException;

    /**
     * Returns the message headers as {@link InputStream} or <code>null</code>
     * if no message can be found for the given <code>uid</code>
     */
    public abstract InputStream getMessageHeaders(long uid) throws IOException;

    /**
     * Return the full message (headers + body) as {@link InputStream} or
     * <code>null</code> if no message can be found for the given
     * <code>uid</code>
     */
    public abstract InputStream getMessage(long uid) throws IOException;

    @Override
    public InputStream getMessage(String uid) throws NumberFormatException, IOException {
        return this.getMessage(Long.parseLong(uid));
    }

    /**
     * Remove the messages with the given uids
     */
    public abstract void remove(long... uids) throws IOException;

    @Override
    public void remove(String... uids) throws NumberFormatException, IOException {
        long[] imapUids = Arrays.stream(uids)
            .mapToLong(Long::parseLong)
            .toArray();
        this.remove(imapUids);
    }

}
