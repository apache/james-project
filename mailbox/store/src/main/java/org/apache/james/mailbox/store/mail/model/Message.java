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

import static org.apache.james.mailbox.model.Content.BUFFER_SIZE;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;

import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.ThreadInformation;
import org.apache.james.mailbox.store.mail.model.impl.Properties;
import org.apache.james.util.ReactorUtils;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

public interface Message {

    MessageId getMessageId();

    Date getInternalDate();

    /**
     * Gets the body content of the document. Headers are excluded.
     *
     * Be aware that this method need to return a new fresh {@link InputStream}
     * on every call, which basicly means it need to start at position 0
     * @return body, not null
     */
    InputStream getBodyContent() throws IOException;

    default Publisher<ByteBuffer> getBodyContentReactive() {
        try {
            return ReactorUtils.toChunks(getBodyContent(), BUFFER_SIZE)
                .subscribeOn(Schedulers.boundedElastic());
        } catch (IOException e) {
            return Flux.error(e);
        }
    }

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
     */
    long getBodyOctets();

    /**
     * The number of octets contained in the full content of this document.
     */
    long getFullContentOctets();

    /**
     * The number of octets contained in the header content of this document.
     */
    long getHeaderOctets();

    /**
     * Gets the number of CRLF in a textual document.
     * @return CRLF count when document is textual,
     * null otherwise
     */
    Long getTextualLineCount();

    /**
     * Gets the header as {@link InputStream}. This MUST INCLUDE the CRLF terminator
     *
     * Be aware that this method need to return a new fresh {@link InputStream}
     * on every call
     */
    InputStream getHeaderContent() throws IOException;


    default Publisher<ByteBuffer> getHeaderContentReactive() {
        try {
            return ReactorUtils.toChunks(getHeaderContent(), BUFFER_SIZE)
                .subscribeOn(Schedulers.boundedElastic());
        } catch (IOException e) {
            return Flux.error(e);
        }
    }

    /**
     * Returns the full raw content of the MailboxMessage via an {@link InputStream}.
     *
     * Be aware that this method need to return a new fresh {@link InputStream}
     * on every call
     */
    InputStream getFullContent() throws IOException;


    default Publisher<ByteBuffer> getFullContentReactive() {
        try {
            return ReactorUtils.toChunks(getFullContent(), BUFFER_SIZE)
                .subscribeOn(Schedulers.boundedElastic());
        } catch (IOException e) {
            return Flux.error(e);
        }
    }

    /**
     * Gets a read-only list of meta-data properties.
     * For properties with multiple values, this list will contain
     * several entries with the same namespace and local name.
     *
     * @return unmodifiable list of meta-data, not null
     */
    Properties getProperties();
    
    /**
     * Return the list of attachments
     * 
     * @return a read only list of attachments
     */
    List<MessageAttachmentMetadata> getAttachments();

    ThreadInformation threadInformation();
}
