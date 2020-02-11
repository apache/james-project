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
import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;
import static org.apache.james.blob.api.BlobStore.StoragePolicy.SIZE_BASED;
import static org.apache.james.blob.mail.MimeMessagePartsId.BODY_BLOB_TYPE;
import static org.apache.james.blob.mail.MimeMessagePartsId.HEADER_BLOB_TYPE;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobType;
import org.apache.james.blob.api.Store;
import org.apache.james.util.BodyOffsetInputStream;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

public class MimeMessageStore {

    public static class Factory {
        private final BlobStore blobStore;

        @Inject
        public Factory(BlobStore blobStore) {
            this.blobStore = blobStore;
        }

        public Store<MimeMessage, MimeMessagePartsId> mimeMessageStore() {
            return new Store.Impl<>(
                new MimeMessagePartsId.Factory(),
                new MimeMessageEncoder(),
                new MimeMessageDecoder(),
                blobStore);
        }
    }

    static class MimeMessageEncoder implements Store.Impl.Encoder<MimeMessage> {
        @Override
        public Stream<Pair<BlobType, Store.Impl.ValueToSave>> encode(MimeMessage message) {
            try {
                byte[] messageAsArray = messageToArray(message);
                int bodyStartOctet = computeBodyStartOctet(messageAsArray);
                byte[] headerBytes = getHeaderBytes(messageAsArray, bodyStartOctet);
                byte[] bodyBytes = getBodyBytes(messageAsArray, bodyStartOctet);
                return Stream.of(
                    Pair.of(HEADER_BLOB_TYPE, new Store.Impl.BytesToSave(headerBytes, SIZE_BASED)),
                    Pair.of(BODY_BLOB_TYPE, new Store.Impl.BytesToSave(bodyBytes, LOW_COST)));
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

    static class MimeMessageDecoder implements Store.Impl.Decoder<MimeMessage> {
        @Override
        public MimeMessage decode(Stream<Pair<BlobType, byte[]>> streams) {
            Preconditions.checkNotNull(streams);
            Map<BlobType,byte[]> pairs = streams.collect(ImmutableMap.toImmutableMap(Pair::getLeft, Pair::getRight));
            Preconditions.checkArgument(pairs.containsKey(HEADER_BLOB_TYPE));
            Preconditions.checkArgument(pairs.containsKey(BODY_BLOB_TYPE));

            return toMimeMessage(
                new SequenceInputStream(
                    new ByteArrayInputStream(pairs.get(HEADER_BLOB_TYPE)),
                    new ByteArrayInputStream(pairs.get(BODY_BLOB_TYPE))));
        }

        private MimeMessage toMimeMessage(InputStream inputStream) {
            try {
                return new MimeMessage(Session.getInstance(new Properties()), inputStream);
            } catch (MessagingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static Factory factory(BlobStore blobStore) {
        return new Factory(blobStore);
    }
}
