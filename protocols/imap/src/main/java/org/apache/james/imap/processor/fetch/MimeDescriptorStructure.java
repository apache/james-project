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

/**
 * 
 */
package org.apache.james.imap.processor.fetch;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.james.imap.message.response.FetchResponse;
import org.apache.james.imap.message.response.FetchResponse.Envelope;
import org.apache.james.imap.message.response.FetchResponse.Structure;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MimeDescriptor;

final class MimeDescriptorStructure implements FetchResponse.Structure {

    private final MimeDescriptor descriptor;

    private final List<String> parameters;

    private final List<Structure> parts;

    private final String disposition;

    private final Map<String, String> dispositionParams;

    private final String location;

    private final String md5;

    private final List<String> languages;

    private final Structure embeddedMessageStructure;

    private final Envelope envelope;

    public MimeDescriptorStructure(boolean allowExtensions, MimeDescriptor descriptor, EnvelopeBuilder builder) throws MailboxException {
        super();
        this.descriptor = descriptor;
        parameters = createParameters(descriptor);
        parts = createParts(allowExtensions, descriptor, builder);

        languages = descriptor.getLanguages();
        this.dispositionParams = descriptor.getDispositionParams();
        this.disposition = descriptor.getDisposition();

        this.md5 = descriptor.getContentMD5();
        this.location = descriptor.getContentLocation();

        final MimeDescriptor embeddedMessage = descriptor.embeddedMessage();
        if (embeddedMessage == null) {
            embeddedMessageStructure = null;
            envelope = null;
        } else {
            embeddedMessageStructure = new MimeDescriptorStructure(allowExtensions, embeddedMessage, builder);
            envelope = builder.buildEnvelope(embeddedMessage);
        }
    }

    private static List<Structure> createParts(boolean allowExtensions, MimeDescriptor descriptor, EnvelopeBuilder builder) throws MailboxException {
        final List<Structure> results = new ArrayList<>();
        for (Iterator<MimeDescriptor> it = descriptor.parts(); it.hasNext();) {
            final MimeDescriptor partDescriptor = it.next();
            results.add(new MimeDescriptorStructure(allowExtensions, partDescriptor, builder));
        }
        return results;
    }

    private static List<String> createParameters(MimeDescriptor descriptor) throws MailboxException {
        final List<String> results = new ArrayList<>();
        // TODO: consider revising this
        for (Map.Entry<String, String> entry : descriptor.contentTypeParameters().entrySet()) {
            results.add(entry.getKey());
            results.add(entry.getValue());
        }
        return results;
    }

    /**
     * @see org.apache.james.imap.message.response.FetchResponse.Structure#getDescription()
     */
    public String getDescription() {
        return descriptor.getContentDescription();
    }

    /**
     * @see org.apache.james.imap.message.response.FetchResponse.Structure#getEncoding()
     */
    public String getEncoding() {
        return descriptor.getTransferContentEncoding();
    }

    /**
     * @see org.apache.james.imap.message.response.FetchResponse.Structure#getId()
     */
    public String getId() {
        return descriptor.getContentID();
    }

    /**
     * @see org.apache.james.imap.message.response.FetchResponse.Structure#getLines()
     */
    public long getLines() {
        return descriptor.getLines();
    }

    /**
     * @see org.apache.james.imap.message.response.FetchResponse.Structure#getMediaType()
     */
    public String getMediaType() {
        return descriptor.getMimeType();
    }

    /**
     * @see org.apache.james.imap.message.response.FetchResponse.Structure#getOctets()
     */
    public long getOctets() {
        return descriptor.getBodyOctets();
    }

    /**
     * @see org.apache.james.imap.message.response.FetchResponse.Structure#getParameters()
     */
    public List<String> getParameters() {
        return parameters;
    }

    /**
     * @see org.apache.james.imap.message.response.FetchResponse.Structure#getSubType()
     */
    public String getSubType() {
        return descriptor.getMimeSubType();
    }

    /**
     * @see org.apache.james.imap.message.response.FetchResponse.Structure#parts()
     */
    public Iterator<Structure> parts() {
        return parts.iterator();
    }

    /**
     * @see org.apache.james.imap.message.response.FetchResponse.Structure#getDisposition()
     */
    public String getDisposition() {
        return disposition;
    }

    /**
     * @see org.apache.james.imap.message.response.FetchResponse.Structure#getLocation()
     */
    public String getLocation() {
        return location;
    }

    /**
     * @see org.apache.james.imap.message.response.FetchResponse.Structure#getMD5()
     */
    public String getMD5() {
        return md5;
    }

    /**
     * @see org.apache.james.imap.message.response.FetchResponse.Structure#getLanguages()
     */
    public List<String> getLanguages() {
        return languages;
    }

    /**
     * @see org.apache.james.imap.message.response.FetchResponse.Structure#getBody()
     */
    public Structure getBody() {
        return embeddedMessageStructure;
    }

    /**
     * @see org.apache.james.imap.message.response.FetchResponse.Structure#getDispositionParams()
     */
    public Map<String, String> getDispositionParams() {
        return dispositionParams;
    }

    /**
     * @see org.apache.james.imap.message.response.FetchResponse.Structure#getEnvelope()
     */
    public Envelope getEnvelope() {
        return envelope;
    }

}