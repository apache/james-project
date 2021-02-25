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

import static org.apache.james.blob.api.BlobStore.StoragePolicy.SIZE_BASED;
import static org.apache.james.blob.mail.MimeMessagePartsId.BODY_BLOB_TYPE;
import static org.apache.james.blob.mail.MimeMessagePartsId.HEADER_BLOB_TYPE;

import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobType;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.api.Store.CloseableByteSource;
import org.apache.james.lifecycle.api.Disposable;
import org.apache.james.server.core.MailHeaders;
import org.apache.james.server.core.MimeMessageInputStream;
import org.apache.james.server.core.MimeMessageSource;
import org.apache.james.server.core.MimeMessageWrapper;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteSource;

import reactor.core.publisher.Mono;

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
            Preconditions.checkNotNull(message);
            return Stream.of(
                Pair.of(HEADER_BLOB_TYPE, (bucketName, blobStore) -> {
                    try {
                        MimeMessageInputStream stream = new MimeMessageInputStream(message);
                        MailHeaders mailHeaders = new MailHeaders(stream);
                        return Mono.from(blobStore.save(bucketName, mailHeaders.toByteArray(), SIZE_BASED));
                    } catch (MessagingException e) {
                        throw new RuntimeException(e);
                    }
                }),
                Pair.of(BODY_BLOB_TYPE, (bucketName, blobStore) ->
                    Mono.from(blobStore.save(bucketName, new ByteSource() {
                        @Override
                        public InputStream openStream() throws IOException {
                            try {
                                MimeMessageInputStream stream = new MimeMessageInputStream(message);
                                new MailHeaders(stream);
                                return stream;
                            } catch (MessagingException e) {
                                throw new IOException("Failed to generate body stream", e);
                            }
                        }

                        @Override
                        public long size() throws IOException {
                            try {
                                return message.getSize();
                            } catch (MessagingException e) {
                                throw new IOException("Failed accessing body size", e);
                            }
                        }
                    }, SIZE_BASED))));
        }
    }

    static class MimeMessageDecoder implements Store.Impl.Decoder<MimeMessage> {
        @Override
        public MimeMessage decode(Stream<Pair<BlobType, CloseableByteSource>> streams) {
            Preconditions.checkNotNull(streams);
            Map<BlobType, CloseableByteSource> pairs = streams.collect(ImmutableMap.toImmutableMap(Pair::getLeft, Pair::getRight));
            Preconditions.checkArgument(pairs.containsKey(HEADER_BLOB_TYPE));
            Preconditions.checkArgument(pairs.containsKey(BODY_BLOB_TYPE));

            return toMimeMessage(
                    pairs.get(HEADER_BLOB_TYPE),
                    pairs.get(BODY_BLOB_TYPE));
        }

        private MimeMessage toMimeMessage(CloseableByteSource headers, CloseableByteSource body) {
            return new MimeMessageWrapper(new MimeMessageBytesSource(headers, body));
        }
    }

    public static Factory factory(BlobStore blobStore) {
        return new Factory(blobStore);
    }

    private static class MimeMessageBytesSource extends MimeMessageSource implements Disposable {
        private final CloseableByteSource headers;
        private final CloseableByteSource body;
        private final String sourceId;

        private MimeMessageBytesSource(CloseableByteSource headers, CloseableByteSource body) {
            this.headers = headers;
            this.body = body;
            this.sourceId = UUID.randomUUID().toString();
        }

        @Override
        public String getSourceId() {
            return sourceId;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new SequenceInputStream(
                headers.openStream(),
                body.openStream());
        }

        @Override
        public long getMessageSize() throws IOException {
            return headers.size() + body.size();
        }

        @Override
        public void dispose() {
            try {
                headers.close();
                body.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
