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
import java.util.List;

/**
 * A Mailbox which is used in POP3 to retrieve messages
 * 
 * 
 */
public interface Mailbox {

    /**
     * Returns the message body as {@link InputStream} or <code>null</code> if
     * no message can be found for the given <code>uid</code>
     * 
     * @param uid
     * @return body
     * @throws IOException
     */
    InputStream getMessageBody(String uid) throws IOException;

    /**
     * Returns the message headers as {@link InputStream} or <code>null</code>
     * if no message can be found for the given <code>uid</code>
     * 
     * @param uid
     * @return headers
     * @throws IOException
     */
    InputStream getMessageHeaders(String uid) throws IOException;

    /**
     * Return the full message (headers + body) as {@link InputStream} or
     * <code>null</code> if no message can be found for the given
     * <code>uid</code>
     * 
     * @param uid
     * @return message
     * @throws IOException
     */
    InputStream getMessage(String uid) throws IOException;

    /**
     * Return a immutable {@link List} which holds the {@link MessageMetaData}
     * for all messages in the {@link Mailbox}
     * 
     * @return messages
     * @throws IOException
     */
    List<MessageMetaData> getMessages() throws IOException;

    /**
     * Remove the messages with the given uids
     * 
     * @param uids
     */
    void remove(String... uids) throws IOException;

    /**
     * Return the identifier for the mailbox. This MUST not change
     * 
     * @return identifer
     * @throws IOException
     */
    String getIdentifier() throws IOException;

    /**
     * Close the mailbox, Any futher attempt to access or change the
     * {@link Mailbox}'s content will fail
     * 
     * @throws IOException
     */
    void close() throws IOException;

}
