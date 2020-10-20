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
package org.apache.james.mailbox.maildir.mail.model;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.mail.util.SharedFileInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.maildir.MaildirMessageName;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ParsedAttachment;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.mail.model.impl.Properties;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.streaming.CountingInputStream;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.message.DefaultBodyDescriptorBuilder;
import org.apache.james.mime4j.message.MaximalBodyDescriptor;
import org.apache.james.mime4j.stream.EntityState;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.stream.MimeTokenStream;
import org.apache.james.mime4j.stream.RecursionMode;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.io.ByteStreams;

public class MaildirMessage implements Message {

    private final MaildirMessageName messageName;
    private int bodyStartOctet;
    private final PropertyBuilder propertyBuilder = new PropertyBuilder();
    private boolean parsed;

    public MaildirMessage(MaildirMessageName messageName) {
        this.messageName = messageName;
    }

    /**
     * Parse message if needed
     */
    private synchronized void parseMessage() {
        if (parsed) {
            return;
        }
        try (SharedFileInputStream tmpMsgIn = new SharedFileInputStream(messageName.getFile())) {

            bodyStartOctet = bodyStartOctet(tmpMsgIn);

            // Disable line length... This should be handled by the smtp server
            // component and not the parser itself
            // https://issues.apache.org/jira/browse/IMAP-122
            final MimeTokenStream parser = new MimeTokenStream(MimeConfig.PERMISSIVE, new DefaultBodyDescriptorBuilder());
            parser.setRecursionMode(RecursionMode.M_NO_RECURSE);
            parser.parse(tmpMsgIn.newStream(0, -1));

            EntityState next = parser.next();
            while (next != EntityState.T_BODY && next != EntityState.T_END_OF_STREAM && next != EntityState.T_START_MULTIPART) {
                next = parser.next();
            }
            final MaximalBodyDescriptor descriptor = (MaximalBodyDescriptor) parser.getBodyDescriptor();
            final String mediaType;
            final String mediaTypeFromHeader = descriptor.getMediaType();
            final String subType;
            if (mediaTypeFromHeader == null) {
                mediaType = "text";
                subType = "plain";
            } else {
                mediaType = mediaTypeFromHeader;
                subType = descriptor.getSubType();
            }
            propertyBuilder.setMediaType(mediaType);
            propertyBuilder.setSubType(subType);
            propertyBuilder.setContentID(descriptor.getContentId());
            propertyBuilder.setContentDescription(descriptor.getContentDescription());
            propertyBuilder.setContentLocation(descriptor.getContentLocation());
            propertyBuilder.setContentMD5(descriptor.getContentMD5Raw());
            propertyBuilder.setContentTransferEncoding(descriptor.getTransferEncoding());
            propertyBuilder.setContentLanguage(descriptor.getContentLanguage());
            propertyBuilder.setContentDispositionType(descriptor.getContentDispositionType());
            propertyBuilder.setContentDispositionParameters(descriptor.getContentDispositionParameters());
            propertyBuilder.setContentTypeParameters(descriptor.getContentTypeParameters());
            // Add missing types
            final String codeset = descriptor.getCharset();
            if (codeset == null) {
                if ("TEXT".equalsIgnoreCase(mediaType)) {
                    propertyBuilder.setCharset("us-ascii");
                }
            } else {
                propertyBuilder.setCharset(codeset);
            }

            final String boundary = descriptor.getBoundary();
            if (boundary != null) {
                propertyBuilder.setBoundary(boundary);
            }
            if ("text".equalsIgnoreCase(mediaType)) {
                long lines = -1;
                try (CountingInputStream bodyStream = new CountingInputStream(parser.getInputStream())) {
                    bodyStream.readAll();
                    lines = bodyStream.getLineCount();
                }

                next = parser.next();
                if (next == EntityState.T_EPILOGUE) {
                    try (CountingInputStream epilogueStream = new CountingInputStream(parser.getInputStream())) {
                        epilogueStream.readAll();
                        lines += epilogueStream.getLineCount();
                    }
                }
                propertyBuilder.setTextualLineCount(lines);
            }
        } catch (IOException | MimeException e) {
            // has successfully been parsen when appending, shouldn't give any
            // problems
        } finally {
            // ignore on close
            parsed = true;
        }
    }


    /**
     * Return the position in the given {@link InputStream} at which the Body of
     * the MailboxMessage starts
     */
    private int bodyStartOctet(InputStream msgIn) throws IOException {
        // we need to pushback maximal 3 bytes
        PushbackInputStream in = new PushbackInputStream(msgIn, 3);
        int localBodyStartOctet = in.available();
        int i;
        int count = 0;
        while ((i = in.read()) != -1 && in.available() > 4) {
            if (i == 0x0D) {
                int a = in.read();
                if (a == 0x0A) {
                    int b = in.read();

                    if (b == 0x0D) {
                        int c = in.read();

                        if (c == 0x0A) {
                            localBodyStartOctet = count + 4;
                            break;
                        }
                        in.unread(c);
                    }
                    in.unread(b);
                }
                in.unread(a);
            }
            count++;
        }
        return localBodyStartOctet;
    }

    @Override
    public String getMediaType() {
        parseMessage();
        return propertyBuilder.build().getMediaType();
    }

    @Override
    public String getSubType() {
        parseMessage();
        return propertyBuilder.build().getSubType();
    }

    @Override
    public long getBodyOctets() {
        return getFullContentOctets() - getBodyStartOctet();
    }

    @Override
    public long getFullContentOctets() {
        Long size = messageName.getSize();
        if (size != null) {
            return size;
        } else {
            try {
                return messageName.getFile().length();
            } catch (FileNotFoundException e) {
                return -1;
            }
        }
    }

    @Override
    public long getHeaderOctets() {
        return getBodyStartOctet();
    }

    @Override
    public Long getTextualLineCount() {
        parseMessage();
        return propertyBuilder.build().getTextualLineCount();
    }

    @Override
    public Properties getProperties() {
        parseMessage();
        return propertyBuilder.build();
    }

    @Override
    public MessageId getMessageId() {
        return new DefaultMessageId();
    }

    @Override
    public Date getInternalDate() {
        return messageName.getInternalDate();
    }

    /**
     * Return the full content of the message via a {@link FileInputStream}
     */
    @Override
    public InputStream getFullContent() throws IOException {
        return new FileInputStream(messageName.getFile());
    }

    @Override
    public InputStream getBodyContent() throws IOException {
        parseMessage();
        FileInputStream body = new FileInputStream(messageName.getFile());
        IOUtils.skipFully(body, bodyStartOctet);
        return body;

    }

    private int getBodyStartOctet() {
        parseMessage();
        return bodyStartOctet;
    }

    @Override
    public InputStream getHeaderContent() throws IOException {
        parseMessage();
        long limit = getBodyStartOctet();
        if (limit < 0) {
            limit = 0;
        }

        return ByteStreams.limit(new FileInputStream(messageName.getFile()), limit);
    }

    @Override
    public List<MessageAttachmentMetadata> getAttachments() {
        try (InputStream fullContent = getFullContent()) {
            AtomicInteger counter = new AtomicInteger(0);
            return new MessageParser().retrieveAttachments(fullContent)
                .stream()
                .map(Throwing.<ParsedAttachment, MessageAttachmentMetadata>function(
                    attachmentMetadata -> attachmentMetadata.asMessageAttachment(generateFixedAttachmentId(counter.incrementAndGet())))
                    .sneakyThrow())
                .collect(Guavate.toImmutableList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private AttachmentId generateFixedAttachmentId(int position) {
        return AttachmentId.from(messageName.getFullName() + "-" + position);
    }

}
