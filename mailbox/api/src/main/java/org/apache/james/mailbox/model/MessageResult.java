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

package org.apache.james.mailbox.model;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;


/**
 * <p>
 * Used to get specific information about a Message without dealing with a
 * MimeMessage instance. Demanded information can be requested by binary
 * combining the constants.
 * </p>
 * 
 * <p>
 * I came to the Idea of the MessageResult because there are many possible
 * combinations of different requests (uid, msn, MimeMessage, Flags).
 * </p>
 * <p>
 * e.g. I want to have all uids, msns and flags of all messages. (a common IMAP
 * operation) Javamail would do it that way:
 * <ol>
 * <li>get all Message objects (Message[])</li>
 * <li>call Message.getMessageNumber()</li>
 * <li>call Message.getFlags()</li>
 * <li>call Folder.getUid(Message)</li>
 * </ol>
 * <p>
 * This means creating a lazy-loading MimeMessage instance. </br> So why don't
 * call getMessages(MessageResult.UID | MessageResult.MSN |
 * MessageResult.FLAGS)? This would leave a lot of room for the implementation
 * to optimize
 * </p>
 */

public interface MessageResult extends Comparable<MessageResult> {
    MessageId getMessageId();

    ThreadId getThreadId();

    Optional<Date> getSaveDate();

    Date getInternalDate();

    Flags getFlags();

    long getSize();

    MessageMetaData messageMetaData();

    MessageUid getUid();

    ModSeq getModSeq();

    MimeDescriptor getMimeDescriptor() throws MailboxException;

    MailboxId getMailboxId();

    /**
     * Iterates the message headers for the given part in a multipart message.
     * 
     * @param path
     *            describing the part's position within a multipart message
     * @return <code>Header</code> <code>Iterator</code>, or null when
     *         {@link FetchGroup#profiles()} does not include the index and
     *         when the mime part cannot be found
     */
    Iterator<Header> iterateHeaders(MimePath path) throws MailboxException;

    /**
     * Iterates the MIME headers for the given part in a multipart message.
     * 
     * @param path
     *            describing the part's position within a multipart message
     * @return <code>Header</code> <code>Iterator</code>, or null when
     *         {@link FetchGroup#profiles()} does not include the index and
     *         when the mime part cannot be found
     */
    Iterator<Header> iterateMimeHeaders(MimePath path) throws MailboxException;

    /**
     * Gets the full message including headers and body. The message data should
     * have normalised line endings (CRLF).
     * 
     * @return <code>Content</code>, or or null if
     *         {@link FetchGroup#FULL_CONTENT} has not been included in the
     *         results
     */
    Content getFullContent() throws MailboxException, IOException;

    /**
     * Gets the full content of the given mime part.
     * 
     * @param path
     *            describes the part
     * @return <code>Content</code>, or null when
     *         {@link FetchGroup#profiles()} did not been include the given
     *         index and when the mime part cannot be found
     */
    Content getFullContent(MimePath path) throws MailboxException;

    /**
     * Gets the body of the message excluding headers. The message data should
     * have normalised line endings (CRLF).
     * 
     * @return <code>Content</code>, or or null if
     *         {@link FetchGroup#FULL_CONTENT} has not been included in the
     *         results
     */
    Content getBody() throws MailboxException, IOException;

    /**
     * Gets the body of the given mime part.
     * 
     * @param path
     *            describes the part
     * @return <code>Content</code>, or null when
     *         {@link FetchGroup#profiles()} did not been include the given
     *         index and when the mime part cannot be found
     */
    Content getBody(MimePath path) throws MailboxException;

    /**
     * Gets the body of the given mime part.
     * 
     * @param path
     *            describes the part
     * @return <code>Content</code>, or null when
     *         {@link FetchGroup#profiles()} did not been include the given
     *         index and when the mime part cannot be found
     */
    Content getMimeBody(MimePath path) throws MailboxException;

    
    Headers getHeaders() throws MailboxException;

    /**
     * Returns the list of loaded attachments depending on the fetchType.
     *
     * These attachments will be loaded only for Full
     */
    List<MessageAttachmentMetadata> getLoadedAttachments() throws MailboxException;

}
