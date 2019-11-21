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
import java.util.Set;

import javax.mail.Flags;

import org.apache.james.mailbox.MessageUid;
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

    Date getInternalDate();

    Flags getFlags();

    long getSize();

    MessageMetaData messageMetaData();

    MessageUid getUid();

    long getModSeq();

    /**
     * Indicates the results fetched.
     */
    interface FetchGroup {

        /**
         * For example: could have best performance when doing store and then
         * forget. UIDs are always returned
         */
        int MINIMAL = 0x00;

        /**
         * 
         */
        int MIME_DESCRIPTOR = 0x01;

        int HEADERS = 0x100;

        int FULL_CONTENT = 0x200;

        int BODY_CONTENT = 0x400;

        int MIME_HEADERS = 0x800;

        int MIME_CONTENT = 0x1000;

        /**
         * Contents to be fetched. Composed bitwise.
         * 
         * @return bitwise description
         * @see #MINIMAL
         * @see #MIME_DESCRIPTOR
         * @see #HEADERS
         * @see #FULL_CONTENT
         * @see #BODY_CONTENT
         * @see #MIME_HEADERS
         * @see #MIME_CONTENT
         */
        int content();

        /**
         * Gets contents to be fetched for contained parts. For each part to be
         * contained, only one descriptor should be contained.
         * 
         * @return <code>Set</code> of {@link PartContentDescriptor}, or null if
         *         there is no part content to be fetched
         */
        Set<PartContentDescriptor> getPartContentDescriptors();

        /**
         * Describes the contents to be fetched for a mail part. All
         * implementations MUST implement equals. Two implementations are equal
         * if and only if their paths are equal.
         */
        interface PartContentDescriptor {
            /**
             * Contents to be fetched. Composed bitwise.
             * 
             * @return bitwise descripion
             * @see #MINIMAL
             * @see #MIME_DESCRIPTOR
             * @see #HEADERS
             * @see #FULL_CONTENT
             * @see #BODY_CONTENT
             * @see #MIME_HEADERS
             * @see #MIME_CONTENT
             */
            int content();

            /**
             * Path describing the part to be fetched.
             * 
             * @return path describing the part, not null
             */
            MimePath path();
        }
    }

    MimeDescriptor getMimeDescriptor() throws MailboxException;

    MailboxId getMailboxId();

    /**
     * Iterates the message headers for the given part in a multipart message.
     * 
     * @param path
     *            describing the part's position within a multipart message
     * @return <code>Header</code> <code>Iterator</code>, or null when
     *         {@link FetchGroup#content()} does not include the index and
     *         when the mime part cannot be found
     */
    Iterator<Header> iterateHeaders(MimePath path) throws MailboxException;

    /**
     * Iterates the MIME headers for the given part in a multipart message.
     * 
     * @param path
     *            describing the part's position within a multipart message
     * @return <code>Header</code> <code>Iterator</code>, or null when
     *         {@link FetchGroup#content()} does not include the index and
     *         when the mime part cannot be found
     */
    Iterator<Header> iterateMimeHeaders(MimePath path) throws MailboxException;

    /**
     * A header.
     */
    interface Header extends Content {

        /**
         * Gets the name of this header.
         * 
         * @return name of this header
         */
        String getName();

        /**
         * Gets the (unparsed) value of this header.
         * 
         * @return value of this header
         */
        String getValue();
    }

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
     *         {@link FetchGroup#content()} did not been include the given
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
     *         {@link FetchGroup#content()} did not been include the given
     *         index and when the mime part cannot be found
     */
    Content getBody(MimePath path) throws MailboxException;

    /**
     * Gets the body of the given mime part.
     * 
     * @param path
     *            describes the part
     * @return <code>Content</code>, or null when
     *         {@link FetchGroup#content()} did not been include the given
     *         index and when the mime part cannot be found
     */
    Content getMimeBody(MimePath path) throws MailboxException;

    
    Headers getHeaders() throws MailboxException;

    /**
     * Returns the list of loaded attachments depending on the fetchType.
     *
     * These attachments will be loaded only for Full
     */
    List<MessageAttachment> getLoadedAttachments() throws MailboxException;

    /**
     * Indicates if the message have attachments, regardless of loaded attachments.
     */
    boolean hasAttachments() throws MailboxException;
    
    /**
     * Describes a path within a multipart MIME message. All implementations
     * must implement equals. Two paths are equal if and only if each position
     * is identical.
     */
    interface MimePath {

        /**
         * Gets the positions of each part in the path.
         * 
         * @return part positions describing the path
         */
        int[] getPositions();
    }
}
