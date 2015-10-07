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
package org.apache.james.mailbox.store.mail.model;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;

import javax.mail.Flags;

/**
 * A MIME message, consisting of meta-data (including MIME headers)
 * plus body content. In the case of multipart documents, this body content
 * has internal structure described by the meta-data.
 */
public interface Message<Id extends MailboxId> extends Comparable<Message<Id>>{

    Date getInternalDate();

    /**
     * Return the mailbox id of the linked mailbox
     * 
     * @return mailboxId
     */
    Id getMailboxId();

    /**
     * Return the uid
     * 
     * @return uid
     */
    long getUid();
    
    /**
     * Set the uid for the message. This must be called before the message is added to the store
     * and must be unique / sequential.
     * 
     * @param uid
     */
    void setUid(long uid);

    
    
    /**
     * Set the mod-sequence for the message. This must be called before the message is added to the store 
     * or any flags are changed. This must be unique / sequential.
     * 
     * @param modSeq
     */
    void setModSeq(long modSeq);
    
    /**
     * Return the mod-sequence for the message
     * 
     * @return message
     */
    long getModSeq();

    /**
     * Return if it was marked as answered
     * 
     * @return answered
     */
    boolean isAnswered();

    /**
     * Return if it was mark as deleted
     * 
     * @return deleted
     */
    boolean isDeleted();

    /**
     * Return if it was mark as draft
     * 
     * @return draft
     */
    boolean isDraft();

    /**
     * Return if it was flagged
     * 
     * @return flagged
     */
    boolean isFlagged();

    /**
     * Return if it was marked as recent
     * 
     * @return recent
     */
    boolean isRecent();

    /**
     * Return if it was marked as seen
     * 
     * @return seen
     */
    boolean isSeen();


    /**
     * Set the Flags 
     * 
     * @param flags
     */
    void setFlags(Flags flags);

    /**
     * Creates a new flags instance populated
     * with the current flag data.
     * 
     * @return new instance, not null
     */
    Flags createFlags();
    
    
    /**
     * Gets the body content of the document. Headers are excluded.
     * 
     * Be aware that this method need to return a new fresh {@link InputStream}
     * on every call, which basicly means it need to start at position 0
     * @return body, not null
     */
    InputStream getBodyContent() throws IOException;

    /**
     * Gets the top level MIME content media type.
     * 
     * @return top level MIME content media type, or null if default
     */
    String getMediaType();

    /**
     * Gets the MIME content subtype.
     * 
     * @return the MIME content subtype, or null if default
     */
    String getSubType();
    
    /**
     * The number of octets contained in the body of this document.
     * 
     * @return number of octets
     */
    long getBodyOctets();
    
    /**
     * The number of octets contained in the full content of this document.
     * 
     * @return number of octets
     */
    long getFullContentOctets();
    
    /**
     * Gets the number of CRLF in a textual document.
     * @return CRLF count when document is textual,
     * null otherwise
     */
    public Long getTextualLineCount();
    
    /**
     * Gets the header as {@link InputStream}. This MUST INCLUDE the CRLF terminator
     * 
     * Be aware that this method need to return a new fresh {@link InputStream}
     * on every call
     * 
     * @return header
     * @throws IOException 
     */
    InputStream getHeaderContent() throws IOException;
    
    /**
     *Returns the full raw content of the Message via an {@link InputStream}.
     *
     * Be aware that this method need to return a new fresh {@link InputStream}
     * on every call
     *
     * @return content
     * @throws IOException
     */
    InputStream getFullContent() throws IOException;

    
    /**
     * Gets a read-only list of meta-data properties.
     * For properties with multiple values, this list will contain
     * several enteries with the same namespace and local name.
     * 
     * @return unmodifiable list of meta-data, not null
     */
    List<Property> getProperties();
}