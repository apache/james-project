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

package org.apache.james.blob.mail;

import static org.apache.commons.io.output.NullOutputStream.NULL_OUTPUT_STREAM;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.apache.commons.io.IOUtils;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.Store;
import org.apache.james.util.BodyOffsetInputStream;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

public class MimeMessageStore extends Store.Impl<MimeMessage> {
    public static final BlobType HEADER_BLOB_TYPE = new BlobType("mailHeader");
    public static final BlobType BODY_BLOB_TYPE = new BlobType("mailBody");

    static class MailEncoder implements Encoder<MimeMessage> {
        @Override
        public Map<BlobType, InputStream> encode(MimeMessage message) {
            try {
                byte[] messageAsArray = messageToArray(message);
                int bodyStartOctet = computeBodyStartOctet(messageAsArray);
                return ImmutableMap.of(
                    HEADER_BLOB_TYPE, new ByteArrayInputStream(getHeaderBytes(messageAsArray, bodyStartOctet)),
                    BODY_BLOB_TYPE, new ByteArrayInputStream(getBodyBytes(messageAsArray, bodyStartOctet)));
            } catch (MessagingException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        private static byte[] messageToArray(MimeMessage message) throws IOException, MessagingException {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            message.writeTo(byteArrayOutputStream);
            return byteArrayOutputStream.toByteArray();
        }

        private static byte[] getHeaderBytes(byte[] messageContentAsArray, int bodyStartOctet) {
            ByteBuffer headerContent = ByteBuffer.wrap(messageContentAsArray, 0, bodyStartOctet);
            byte[] headerBytes = new byte[bodyStartOctet];
            headerContent.get(headerBytes);
            return headerBytes;
        }

        private static byte[] getBodyBytes(byte[] messageContentAsArray, int bodyStartOctet) {
            if (bodyStartOctet < messageContentAsArray.length) {
                ByteBuffer bodyContent = ByteBuffer.wrap(messageContentAsArray,
                    bodyStartOctet,
                    messageContentAsArray.length - bodyStartOctet);
                byte[] bodyBytes = new byte[messageContentAsArray.length - bodyStartOctet];
                bodyContent.get(bodyBytes);
                return bodyBytes;
            } else {
                return new byte[] {};
            }
        }

        private static int computeBodyStartOctet(byte[] messageAsArray) throws IOException {
            try (BodyOffsetInputStream bodyOffsetInputStream =
                     new BodyOffsetInputStream(new ByteArrayInputStream(messageAsArray))) {
                consume(bodyOffsetInputStream);

                if (bodyOffsetInputStream.getBodyStartOffset() == -1) {
                    return 0;
                }
                return (int) bodyOffsetInputStream.getBodyStartOffset();
            }
        }

        private static void consume(InputStream in) throws IOException {
            IOUtils.copy(in, NULL_OUTPUT_STREAM);
        }
    }

    static class MailDecoder implements Decoder<MimeMessage> {
        @Override
        public void validateInput(Collection<BlobType> input) {
            Preconditions.checkArgument(input.contains(HEADER_BLOB_TYPE), "Expecting 'mailHeader' blobId to be specified");
            Preconditions.checkArgument(input.contains(BODY_BLOB_TYPE), "Expecting 'mailBody' blobId to be specified");
            Preconditions.checkArgument(input.size() == 2, "blobId other than 'mailHeader' or 'mailBody' are not supported");
        }

        @Override
        public MimeMessage decode(Map<BlobType, byte[]> streams) {
            Preconditions.checkNotNull(streams);
            Preconditions.checkArgument(streams.containsKey(HEADER_BLOB_TYPE));
            Preconditions.checkArgument(streams.containsKey(BODY_BLOB_TYPE));

            return toMimeMessage(
                new SequenceInputStream(
                    new ByteArrayInputStream(streams.get(HEADER_BLOB_TYPE)),
                    new ByteArrayInputStream(streams.get(BODY_BLOB_TYPE))));
        }

        private MimeMessage toMimeMessage(InputStream inputStream) {
            try {
                return new MimeMessage(Session.getInstance(new Properties()), inputStream);
            } catch (MessagingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Inject
    public MimeMessageStore(BlobStore blobStore) {
        super(new MailEncoder(), new MailDecoder(), blobStore);
    }
}
