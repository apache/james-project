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
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.stream.RawField;
import org.apache.james.mime4j.util.ByteSequence;
import org.apache.james.mime4j.util.ContentUtil;

public class ResultUtils {

    public static List<Header> createHeaders(MailboxMessage document) throws IOException {
        List<Header> results = new ArrayList<>();
        MimeStreamParser parser = new MimeStreamParser(MimeConfig.PERMISSIVE);
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
                    throw new UnsupportedOperationException("Unsupported result: " + fetchGroup.content());
                }
                addPartContent(fetchGroup, message, messageResult);
            }
            return messageResult;
        } catch (IOException | MimeException e) {
            throw new MailboxException("Unable to parse message", e);
        }
    }

    private static boolean haveValidContent(FetchGroup fetchGroup) {
        return fetchGroup.hasOnlyMasks(FetchGroup.HEADERS_MASK,
            FetchGroup.BODY_CONTENT_MASK,
            FetchGroup.FULL_CONTENT_MASK,
            FetchGroup.MIME_DESCRIPTOR_MASK);
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
        if (descriptor.hasMask(FetchGroup.FULL_CONTENT_MASK)) {
            addFullContent(message, messageResult, mimePath);
        }
        if (descriptor.hasMask(FetchGroup.BODY_CONTENT_MASK)) {
            addBodyContent(message, messageResult, mimePath);
        }
        if (descriptor.hasMask(FetchGroup.MIME_CONTENT_MASK)) {
            addMimeBodyContent(message, messageResult, mimePath);
        }
        if (descriptor.hasMask(FetchGroup.HEADERS_MASK)) {
            addHeaders(message, messageResult, mimePath);
        }
        if (descriptor.hasMask(FetchGroup.MIME_HEADERS_MASK)) {
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
            PartContentBuilder builder = build(path, message);
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
        PartContentBuilder builder = build(path, message);
        Content content = builder.getMimeBodyContent();
        messageResult.setMimeBodyContent(mimePath, content);
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
