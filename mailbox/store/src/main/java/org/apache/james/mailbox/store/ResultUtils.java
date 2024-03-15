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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.Header;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MimePath;
import org.apache.james.mailbox.model.PartContentDescriptor;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.streaming.PartContentBuilder;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.parser.AbstractContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.BodyDescriptorBuilder;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.stream.RawField;
import org.apache.james.mime4j.util.ByteSequence;
import org.apache.james.mime4j.util.ContentUtil;

import com.google.common.annotations.VisibleForTesting;

public class ResultUtils {
    private static final EnumSet<FetchGroup.Profile> SUPPORTED_GROUPS = EnumSet.of(
        FetchGroup.Profile.HEADERS,
        FetchGroup.Profile.HEADERS_WITH_ATTACHMENTS_METADATA,
        FetchGroup.Profile.BODY_CONTENT,
        FetchGroup.Profile.FULL_CONTENT,
        FetchGroup.Profile.MIME_DESCRIPTOR);

    private static class NoopBodyDescriptor implements BodyDescriptorBuilder {
        static final NoopBodyDescriptor INSTANCE = new NoopBodyDescriptor();

        @Override
        public void reset() {

        }

        @Override
        public Field addField(RawField rawField) {
            return rawField;
        }

        @Override
        public BodyDescriptor build() {
            return new BodyDescriptor() {
                @Override
                public String getBoundary() {
                    return null;
                }

                @Override
                public String getMimeType() {
                    return null;
                }

                @Override
                public String getMediaType() {
                    return null;
                }

                @Override
                public String getSubType() {
                    return null;
                }

                @Override
                public String getCharset() {
                    return null;
                }

                @Override
                public String getTransferEncoding() {
                    return null;
                }

                @Override
                public long getContentLength() {
                    return 0;
                }
            };
        }

        @Override
        public BodyDescriptorBuilder newChild() {
            return this;
        }
    }

    public static List<Header> createHeaders(MailboxMessage document) throws IOException {
        List<Header> results = new ArrayList<>();
        MimeStreamParser parser = new MimeStreamParser(MimeConfig.PERMISSIVE, null, NoopBodyDescriptor.INSTANCE);

        parser.setContentHandler(new AbstractContentHandler() {
            @Override
            public void endHeader() {
                parser.stop();
            }
            
            @Override
            public void field(Field field) throws MimeException {
                String fieldValue;
                if (field instanceof RawField) {
                    // check if we can access the body in the raw form so no unfolding was done under the hood
                    ByteSequence raw = field.getRaw();
                    int len = raw.length();
                    int off = ((RawField) field).getDelimiterIdx() + 1;
                    if (len > off + 1 && (raw.byteAt(off) & 0xff) == 0x20) {
                        off++;
                    }
                
                    fieldValue = ContentUtil.decode(raw, off, len - off);
                } else {
                    fieldValue = field.getBody();
                }
                if (fieldValue.endsWith("\r\f")) {
                    fieldValue = fieldValue.substring(0,fieldValue.length() - 2);
                }
                if (fieldValue.startsWith(" ")) {
                    fieldValue = fieldValue.substring(1);
                }
                
                Header header = new Header(field.getName(), fieldValue);
                results.add(header);
            }
        });
        try {
            parser.parse(document.getHeaderContent());
        } catch (MimeException e) {
            throw new IOException("Unable to parse headers of message " + document, e);
        }
        return results;
    }
    
    /**
     * Return the {@link MessageResult} for the given {@link MailboxMessage} and {@link FetchGroup}
     */
    public static MessageResult loadMessageResult(MailboxMessage message, FetchGroup fetchGroup) throws MailboxException {
        try {
            MessageResultImpl messageResult = new MessageResultImpl(message);
            if (fetchGroup != null) {
                if (!haveValidContent(fetchGroup)) {
                    throw new UnsupportedOperationException("Unsupported result: " + fetchGroup.profiles());
                }
                addPartContent(fetchGroup, message, messageResult);
            }
            return messageResult;
        } catch (IOException | MimeException e) {
            throw new MailboxException("Unable to parse message", e);
        }
    }

    @VisibleForTesting
    static boolean haveValidContent(FetchGroup fetchGroup) {
        return SUPPORTED_GROUPS.containsAll(fetchGroup.profiles());
    }

    private static void addPartContent(FetchGroup fetchGroup, MailboxMessage message, MessageResultImpl messageResult)
            throws MailboxException, IOException, MimeException {
        Collection<PartContentDescriptor> partContent = fetchGroup.getPartContentDescriptors();
        if (partContent != null) {
            for (PartContentDescriptor descriptor: partContent) {
                addPartContent(descriptor, message, messageResult);
            }
        }
    }

    private static void addPartContent(PartContentDescriptor descriptor, MailboxMessage message, MessageResultImpl messageResult)
            throws MailboxException, IOException, MimeException {
        MimePath mimePath = descriptor.path();
        EnumSet<FetchGroup.Profile> profiles = descriptor.profiles();
        if (profiles.contains(FetchGroup.Profile.FULL_CONTENT)) {
            addFullContent(message, messageResult, mimePath);
        }
        if (profiles.contains(FetchGroup.Profile.BODY_CONTENT)) {
            addBodyContent(message, messageResult, mimePath);
        }
        if (profiles.contains(FetchGroup.Profile.MIME_CONTENT)) {
            addMimeBodyContent(message, messageResult, mimePath);
        }
        if (profiles.contains(FetchGroup.Profile.HEADERS) || profiles.contains(FetchGroup.Profile.HEADERS_WITH_ATTACHMENTS_METADATA)) {
            addHeaders(message, messageResult, mimePath);
        }
        if (profiles.contains(FetchGroup.Profile.MIME_HEADERS)) {
            addMimeHeaders(message, messageResult, mimePath);
        }
    }

    private static PartContentBuilder build(int[] path, MailboxMessage message)
            throws IOException, MimeException {
        InputStream stream = message.getFullContent();
        PartContentBuilder result = new PartContentBuilder();
        result.parse(stream);
        try {
            for (int next : path) {
                result.to(next);
            }
        } catch (PartContentBuilder.PartNotFoundException e) {
            // Missing parts should return zero sized content
            // See http://markmail.org/message/2jconrj7scvdi5dj
            result.markEmpty();
        }
        return result;
    }

    private static PartContentBuilder buildHandleSinglePart(int[] path, MailboxMessage message) throws IOException, MimeException {
        // CF RFC-3501 section 6.4.5
        //
        // Every message has at least one part number.  Non-[MIME-IMB]
        // messages, and non-multipart [MIME-IMB] messages with no
        // encapsulated message, only have a part 1.


        if (!message.getProperties().getMediaType().equalsIgnoreCase("multipart")
            && path.length == 1 && path[0] == 1) {

            InputStream stream = message.getFullContent();
            PartContentBuilder result = new PartContentBuilder();
            result.parse(stream);
            return result;
        }
        InputStream stream = message.getFullContent();
        PartContentBuilder result = new PartContentBuilder();
        result.parse(stream);
        try {
            for (int next : path) {
                result.to(next);
            }
        } catch (PartContentBuilder.PartNotFoundException e) {
            // Missing parts should return zero sized content
            // See http://markmail.org/message/2jconrj7scvdi5dj
            result.markEmpty();
        }
        return result;
    }
  
    private static int[] path(MimePath mimePath) {
        if (mimePath == null) {
            return null;
        } else {
            return mimePath.getPositions();
        }
    }

    private static void addHeaders(MailboxMessage message, MessageResultImpl messageResult, MimePath mimePath)
            throws IOException, MimeException {
        int[] path = path(mimePath);
        if (path != null) {
            PartContentBuilder builder = build(path, message);
            List<Header> headers = builder.getMessageHeaders();
            messageResult.setHeaders(mimePath, headers.iterator());
        }
    }

    private static void addMimeHeaders(MailboxMessage message, MessageResultImpl messageResult, MimePath mimePath)
            throws IOException, MimeException {
        int[] path = path(mimePath);
        if (path != null) {
            PartContentBuilder builder = buildHandleSinglePart(path, message);
            List<Header> headers = builder.getMimeHeaders();
            messageResult.setMimeHeaders(mimePath, headers.iterator());
        }
    }

    private static void addBodyContent(MailboxMessage message, MessageResultImpl messageResult, MimePath mimePath)
            throws IOException, MimeException {
        int[] path = path(mimePath);
        if (path != null) {
            PartContentBuilder builder = build(path, message);
            Content content = builder.getMessageBodyContent();
            messageResult.setBodyContent(mimePath, content);
        }
    }

    private static void addMimeBodyContent(MailboxMessage message, MessageResultImpl messageResult, MimePath mimePath)
            throws IOException, MimeException {
        int[] path = path(mimePath);
        if (path != null) {
            PartContentBuilder builder = build(path, message);
            Content content = builder.getMimeBodyContent();
            messageResult.setMimeBodyContent(mimePath, content);
        }
    }

    private static void addFullContent(MailboxMessage message, MessageResultImpl messageResult, MimePath mimePath)
            throws MailboxException, IOException, MimeException {
        int[] path = path(mimePath);
        if (path != null) {
            PartContentBuilder builder = build(path, message);
            Content content = builder.getFullContent();
            messageResult.setFullContent(mimePath, content);
        }
    }
}
