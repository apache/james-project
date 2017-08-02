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

package org.apache.james.mailbox.store;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MimeDescriptor;
import org.apache.james.mailbox.store.streaming.CountingInputStream;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.message.DefaultBodyDescriptorBuilder;
import org.apache.james.mime4j.message.MaximalBodyDescriptor;
import org.apache.james.mime4j.stream.EntityState;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.stream.MimeTokenStream;
import org.apache.james.mime4j.stream.RecursionMode;

public class MimeDescriptorImpl implements MimeDescriptor {

    private final static Charset US_ASCII = Charset.forName("US-ASCII");
    private static final MimeConfig MIME_ENTITY_CONFIG = MimeConfig.custom()
        .setMaxContentLen(-1)
        .setMaxHeaderCount(-1)
        .setMaxHeaderLen(-1)
        .setMaxHeaderCount(-1)
        .setMaxLineLen(-1)
        .build();

    
    /**
     * Is this a composite media type (as per RFC2045)?
     * 
     * TODO: Move to Mime4j
     * @param mediaType possibly null
     * @return true when the type is composite,
     * false otherwise
     */
    public static boolean isComposite(String mediaType) {
        return "message".equalsIgnoreCase(mediaType) || "multipart".equalsIgnoreCase(mediaType);
    }

    public static MimeDescriptorImpl build(InputStream stream) throws IOException, MimeException {
        // Disable line length limit
        // See https://issues.apache.org/jira/browse/IMAP-132
        //
        final MimeTokenStream parser = new MimeTokenStream(MIME_ENTITY_CONFIG, new DefaultBodyDescriptorBuilder());
        
        parser.parse(stream);
        
        // TODO: Shouldn't this get set before we call the parse ?
        parser.setRecursionMode(RecursionMode.M_NO_RECURSE);
        return createDescriptor(parser);
    }

    private static MimeDescriptorImpl createDescriptor(
            final MimeTokenStream parser) throws IOException, MimeException {
        EntityState next = parser.next();
        final Collection<MessageResult.Header> headers = new ArrayList<>();
        while (next != EntityState.T_BODY
                && next != EntityState.T_END_OF_STREAM
                && next != EntityState.T_START_MULTIPART) {
            if (next == EntityState.T_FIELD) {
                headers.add(new ResultHeader(parser.getField().getName(), parser
                        .getField().getBody().trim()));
            }
            next = parser.next();
        }

        final MimeDescriptorImpl mimeDescriptorImpl;
        switch (next) {
            case T_BODY:
                mimeDescriptorImpl = simplePartDescriptor(parser, headers);
                break;
            case T_START_MULTIPART:
                mimeDescriptorImpl = compositePartDescriptor(parser, headers);
                break;
            case T_END_OF_STREAM:
                throw new MimeException("Premature end of stream");
            default:
                throw new MimeException("Unexpected parse state");
        }
        return mimeDescriptorImpl;
    }

    private static MimeDescriptorImpl compositePartDescriptor(
            final MimeTokenStream parser, Collection<MessageResult.Header> headers)
            throws IOException, MimeException {
        MaximalBodyDescriptor descriptor = (MaximalBodyDescriptor) parser
                .getBodyDescriptor();
        MimeDescriptorImpl mimeDescriptor = createDescriptor(0, 0, descriptor,
                null, headers);
        EntityState next = parser.next();
        while (next != EntityState.T_END_MULTIPART
                && next != EntityState.T_END_OF_STREAM) {
            if (next == EntityState.T_START_BODYPART) {
                mimeDescriptor.addPart(createDescriptor(parser));
            }
            next = parser.next();
        }
        return mimeDescriptor;
    }

    private static MimeDescriptorImpl simplePartDescriptor(
            final MimeTokenStream parser, Collection<MessageResult.Header> headers)
            throws IOException, MimeException {
        MaximalBodyDescriptor descriptor = (MaximalBodyDescriptor) parser
                .getBodyDescriptor();
        final MimeDescriptorImpl mimeDescriptorImpl;
        if ("message".equalsIgnoreCase(descriptor.getMediaType())
                && "rfc822".equalsIgnoreCase(descriptor.getSubType())) {
            final CountingInputStream messageStream = new CountingInputStream(
                    parser.getDecodedInputStream());
            MimeDescriptorImpl embeddedMessageDescriptor = build(messageStream);
            final int octetCount = messageStream.getOctetCount();
            final int lineCount = messageStream.getLineCount();

            mimeDescriptorImpl = createDescriptor(octetCount, lineCount,
                    descriptor, embeddedMessageDescriptor, headers);
        } else {
            final InputStream body = parser.getInputStream();
            long bodyOctets = 0;
            long lines = 0;
            for (int n = body.read(); n >= 0; n = body.read()) {
                if (n == '\r') {
                    lines++;
                }
                bodyOctets++;
            }

            mimeDescriptorImpl = createDescriptor(bodyOctets, lines,
                    descriptor, null, headers);
        }
        return mimeDescriptorImpl;
    }

    private static MimeDescriptorImpl createDescriptor(long bodyOctets,
            long lines, MaximalBodyDescriptor descriptor,
            MimeDescriptor embeddedMessage, Collection<MessageResult.Header> headers) {
        final String contentDescription = descriptor.getContentDescription();
        final String contentId = descriptor.getContentId();

        final String subType = descriptor.getSubType();
        final String type = descriptor.getMediaType();
        final String transferEncoding = descriptor.getTransferEncoding();
        final Map<String, String> contentTypeParameters = new TreeMap<>(descriptor.getContentTypeParameters());
        final String codeset = descriptor.getCharset();
        if (codeset == null) {
            if ("TEXT".equals(type)) {
                contentTypeParameters.put("charset", "us-ascii");
            }
        } else {
            contentTypeParameters.put("charset", codeset);
        }
        final String boundary = descriptor.getBoundary();
        if (boundary != null) {
            contentTypeParameters.put("boundary", boundary);
        }
        
        final List<String> languages = descriptor.getContentLanguage();
        final String disposition = descriptor.getContentDispositionType();
        final Map<String, String> dispositionParams = descriptor
                .getContentDispositionParameters();
        final Collection<MimeDescriptor> parts = new ArrayList<>();
        final String location = descriptor.getContentLocation();
        final String md5 = descriptor.getContentMD5Raw();
        return new MimeDescriptorImpl(
                bodyOctets, contentDescription, contentId, lines, subType,
                type, transferEncoding, headers, contentTypeParameters,
                languages, disposition, dispositionParams, embeddedMessage,
                parts, location, md5);
    }

    private final long bodyOctets;

    private final String contentDescription;

    private final String contentId;

    private final long lines;

    private final String subType;

    private final String type;

    private final String transferEncoding;

    private final List<String> languages;

    private final Collection<MessageResult.Header> headers;

    private final Map<String, String> contentTypeParameters;

    private final String disposition;

    private final Map<String, String> dispositionParams;

    private final MimeDescriptor embeddedMessage;

    private final Collection<MimeDescriptor> parts;

    private final String location;

    private final String md5;


    public MimeDescriptorImpl(long bodyOctets,
            String contentDescription, String contentId,
            long lines, String subType, String type,
            String transferEncoding, Collection<MessageResult.Header> headers,
            Map<String, String> contentTypeParameters, List<String> languages,
            String disposition, Map<String, String> dispositionParams,
            MimeDescriptor embeddedMessage, Collection<MimeDescriptor> parts,
            String location, String md5) {
        super();
        this.type = type;
        this.bodyOctets = bodyOctets;
        this.contentDescription = contentDescription;
        this.contentId = contentId;
        this.lines = lines;
        this.subType = subType;
        this.transferEncoding = transferEncoding;
        this.headers = headers;
        this.contentTypeParameters = contentTypeParameters;
        this.embeddedMessage = embeddedMessage;
        this.parts = parts;
        this.languages = languages;
        this.disposition = disposition;
        this.dispositionParams = dispositionParams;
        this.location = location;
        this.md5 = md5;
    }

    public Map<String, String> contentTypeParameters() {
        return contentTypeParameters;
    }

    public MimeDescriptor embeddedMessage() {
        return embeddedMessage;
    }

    public long getBodyOctets() {
        return bodyOctets;
    }

    public String getContentDescription() {
        return contentDescription;
    }

    public String getContentID() {
        return contentId;
    }

    public long getLines() {
        return lines;
    }

    public String getMimeSubType() {
        return subType;
    }

    public String getMimeType() {
        return type;
    }

    public String getTransferContentEncoding() {
        return transferEncoding;
    }

    public Iterator<MessageResult.Header> headers() {
        return headers.iterator();
    }

    public Iterator<MimeDescriptor> parts() {
        return parts.iterator();
    }

    private void addPart(MimeDescriptor descriptor) {
        parts.add(descriptor);
    }

    public List<String> getLanguages() {
        return languages;
    }

    public String getDisposition() {
        return disposition;
    }

    public Map<String,String> getDispositionParams() {
        return dispositionParams;
    }

    public String getContentLocation() {
        return location;
    }

    public String getContentMD5() {
        return md5;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        StringBuilder sb = new StringBuilder();
        Iterator<MessageResult.Header> hIt = headers.iterator();
        while(hIt.hasNext()) {
            MessageResult.Header header = hIt.next();
            sb.append(header.getName()).append(": " ).append(header.getValue()).append("\r\n");
        }
        sb.append("\r\n");
        return new ByteArrayInputStream(sb.toString().getBytes(US_ASCII));
    }

    @Override
    public long size() throws MailboxException {
        long result = 0;
        for (MessageResult.Header header : headers) {
            if (header != null) {
                result += header.size();
                result += 2;
            }
        }
        
        // Add for CLRF
        result +=2;
        return result;
    }
}
