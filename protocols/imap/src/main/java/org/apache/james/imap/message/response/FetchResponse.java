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
package org.apache.james.imap.message.response;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.mail.Flags;

import org.apache.james.imap.api.message.response.ImapResponseMessage;

public final class FetchResponse implements ImapResponseMessage {

    private final int messageNumber;

    private final Flags flags;

    private final Long uid;

    private final Date internalDate;

    private final Long size;

    private final List<BodyElement> elements;

    private final Envelope envelope;

    private final Structure body;

    private final Structure bodystructure;

    private final Long modSeq;

    public FetchResponse(final int messageNumber, final Flags flags, final Long uid, final Long modSeq, final Date internalDate, final Long size, final Envelope envelope, final Structure body, final Structure bodystructure, List<BodyElement> elements) {
        super();
        this.messageNumber = messageNumber;
        this.flags = flags;
        this.uid = uid;
        this.internalDate = internalDate;
        this.size = size;
        this.envelope = envelope;
        this.elements = elements;
        this.body = body;
        this.bodystructure = bodystructure;
        this.modSeq = modSeq;
    }

    /**
     * Gets the structure of this message.
     * 
     * @return <code>Structure</code>, or null if the <code>FETCH</code> did not
     *         include <code>BODY</code>
     */
    public Structure getBody() {
        return body;
    }

    /**
     * Gets the structure of this message.
     * 
     * @return <code>Structure</code>, or null if the <code>FETCH</code> did not
     *         include <code>BODYSTRUCTURE</code>
     */
    public Structure getBodyStructure() {
        return bodystructure;
    }

    /**
     * Gets the number of the message whose details have been fetched.
     * 
     * @return message number
     */
    public int getMessageNumber() {
        return messageNumber;
    }

    /**
     * Gets the fetched flags.
     * 
     * @return {@link Flags} fetched, or null if the <code>FETCH</code> did not
     *         include <code>FLAGS</code>
     */
    public Flags getFlags() {
        return flags;
    }

    /**
     * Gets the unique id for the fetched message.
     * 
     * @return message uid, or null if the <code>FETCH</code> did not include
     *         <code>UID</code>
     */
    public Long getUid() {
        return uid;
    }

    /**
     * Gets the internal date for the fetched message.
     * 
     * @return the internalDate, or null if the <code>FETCH</code> did not
     *         include <code>INTERNALDATE</code>
     */
    public Date getInternalDate() {
        return internalDate;
    }

    /**
     * Gets the size for the fetched message.
     * 
     * @return the size, or null if the <code>FETCH</code> did not include
     *         <code>SIZE</code>
     */
    public Long getSize() {
        return size;
    }

    /**
     * Gets the envelope for the fetched message
     * 
     * @return the envelope, or null if the <code>FETCH</code> did not include
     *         <code>ENVELOPE</code>
     */
    public Envelope getEnvelope() {
        return envelope;
    }

    /**
     * TODO: replace
     * 
     * @return <code>List</code> of <code>BodyElement</code>'s, or null if the
     *         <code>FETCH</code> did not include body elements
     */
    public List<BodyElement> getElements() {
        return elements;
    }
    
    /**
     * Return the mod-sequence for the message or null if the <code>FETCH</code> did not 
     * include it
     * 
     * @return modSeq
     */
    public Long getModSeq() {
        return modSeq;
    }

    /**
     * Describes the message structure.
     */
    public interface Structure {
        /**
         * Gets the MIME media type.
         * 
         * @return media type, or null if default
         */
        public String getMediaType();

        /**
         * Gets the MIME content subtype
         * 
         * @return subtype of null if default
         */
        public String getSubType();

        /**
         * Gets body type parameters.
         * 
         * @return parameters, or null
         */
        public List<String> getParameters();

        /**
         * Gets <code>Content-ID</code>.
         * 
         * @return MIME content ID, possibly null
         */
        public String getId();

        /**
         * Gets <code>Content-Description</code>.
         * 
         * @return MIME <code>Content-Description</code>, possibly null
         */
        public String getDescription();

        /**
         * Gets content transfer encoding.
         * 
         * @return MIME <code>Content-Transfer-Encoding</code>, possibly null
         */
        public String getEncoding();

        /**
         * Gets the size of message body the in octets.
         * 
         * @return number of octets in the message.
         */
        public long getOctets();

        /**
         * Gets the number of lines fo transfer encoding for a <code>TEXT</code>
         * type.
         * 
         * @return number of lines when <code>TEXT</code>, -1 otherwise
         */
        public long getLines();

        /**
         * Gets <code>Content-MD5</code>.
         * 
         * @return Content-MD5 or null if <code>BODY</code> FETCH or not present
         */
        public String getMD5();

        /**
         * Gets header field-value from <code>Content-Disposition</code>.
         * 
         * @return map of field value <code>String</code> indexed by field name
         *         <code>String</code> or null if <code>BODY</code> FETCH or not
         *         present
         */
        public Map<String, String> getDispositionParams();

        /**
         * Gets header field-value from <code>Content-Disposition</code>.
         * 
         * @return disposition or null if <code>BODY</code> FETCH or not present
         */
        public String getDisposition();

        /**
         * Gets MIME <code>Content-Language</code>'s.
         * 
         * @return List of <code>Content-Language</code> name
         *         <code>String</code>'s possibly null or null when
         *         <code>BODY</code> FETCH
         */
        public List<String> getLanguages();

        /**
         * Gets <code>Content-Location</code>.
         * 
         * @return Content-Location possibly null; or null when
         *         <code>BODY</code> FETCH
         */
        public String getLocation();

        /**
         * Iterates parts of a composite media type.
         * 
         * @return <code>Structure</code> <code>Iterator</code> when composite
         *         type, null otherwise
         */
        public Iterator<Structure> parts();

        /**
         * Gets the envelope of an embedded mail.
         * 
         * @return <code>Envelope</code> when <code>message/rfc822</code>
         *         otherwise null
         */
        public Envelope getEnvelope();

        /**
         * Gets the envelope of an embedded mail.
         * 
         * @return <code>Structure</code> when when <code>message/rfc822</code>
         *         otherwise null
         */
        public Structure getBody();
    }

    /**
     * BODY FETCH element content.
     */
    public interface BodyElement extends Literal {

        /**
         * The full name of the element fetched. As per <code>FETCH</code>
         * command input.
         * 
         * @return name, not null
         */
        public String getName();

    }

    /**
     * ENVELOPE content.
     */
    public interface Envelope {

        /**
         * Gets the envelope <code>date</code>. This is the value of the RFC822
         * <code>date</code> header.
         * 
         * @return envelope Date or null if this attribute is <code>NIL</code>
         */
        public String getDate();

        /**
         * Gets the envelope <code>subject</code>. This is the value of the
         * RFC822 <code>subject</code> header.
         * 
         * @return subject, or null if this attribute is <code>NIL</code>
         */
        public String getSubject();

        /**
         * Gets the envelope <code>from</code> addresses.
         * 
         * @return from addresses, not null
         */
        public Address[] getFrom();

        /**
         * Gets the envelope <code>sender</code> addresses.
         * 
         * @return <code>sender</code> addresses, not null
         */
        public Address[] getSender();

        /**
         * Gets the envelope <code>reply-to</code> addresses.
         * 
         * @return <code>reply-to</code>, not null
         */
        public Address[] getReplyTo();

        /**
         * Gets the envelope <code>to</code> addresses.
         * 
         * @return <code>to</code>, or null if <code>NIL</code>
         */
        public Address[] getTo();

        /**
         * Gets the envelope <code>cc</code> addresses.
         * 
         * @return <code>cc</code>, or null if <code>NIL</code>
         */
        public Address[] getCc();

        /**
         * Gets the envelope <code>bcc</code> addresses.
         * 
         * @return <code>bcc</code>, or null if <code>NIL</code>
         */
        public Address[] getBcc();

        /**
         * Gets the envelope <code>in-reply-to</code>.
         * 
         * @return <code>in-reply-to</code> or null if <code>NIL</code>
         */
        public String getInReplyTo();

        /**
         * Gets the envelope <code>message
         * 
         * @return the message id
         */
        public String getMessageId();

        /**
         * Values an envelope address.
         */
        public interface Address {

            /** Empty array */
            public static final Address[] EMPTY = {};

            /**
             * Gets the personal name.
             * 
             * @return personal name, or null if the personal name is
             *         <code>NIL</code>
             */
            public String getPersonalName();

            /**
             * Gets the SMTP source route.
             * 
             * @return SMTP at-domain-list, or null if the list if
             *         <code>NIL</code>
             */
            public String getAtDomainList();

            /**
             * Gets the mailbox name.
             * 
             * @return the mailbox name or the group name when
             *         {@link #getHostName()} is null
             */
            public String getMailboxName();

            /**
             * Gets the host name.
             * 
             * @return the host name, or null when this address marks the start
             *         or end of a group
             */
            public String getHostName();
        }
    }
}
