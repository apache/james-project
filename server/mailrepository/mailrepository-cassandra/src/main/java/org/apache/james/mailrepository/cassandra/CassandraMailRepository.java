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

package org.apache.james.mailrepository.cassandra;

import static org.apache.commons.io.output.NullOutputStream.NULL_OUTPUT_STREAM;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.ObjectStore;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.util.BodyOffsetInputStream;
import org.apache.james.util.CompletableFutureUtil;
import org.apache.james.util.FluentFutureStream;
import org.apache.mailet.Mail;

import com.github.fge.lambdas.Throwing;
import com.google.common.primitives.Bytes;

public class CassandraMailRepository implements MailRepository {

    private final MailRepositoryUrl url;
    private final CassandraMailRepositoryKeysDAO keysDAO;
    private final CassandraMailRepositoryCountDAO countDAO;
    private final CassandraMailRepositoryMailDAO mailDAO;
    private final ObjectStore objectStore;

    public CassandraMailRepository(MailRepositoryUrl url, CassandraMailRepositoryKeysDAO keysDAO, CassandraMailRepositoryCountDAO countDAO, CassandraMailRepositoryMailDAO mailDAO, ObjectStore objectStore) {
        this.url = url;
        this.keysDAO = keysDAO;
        this.countDAO = countDAO;
        this.mailDAO = mailDAO;
        this.objectStore = objectStore;
    }

    @Override
    public MailKey store(Mail mail) throws MessagingException {
        try {
            MailKey mailKey = MailKey.forMail(mail);
            Pair<byte[], byte[]> splitHeaderBody = splitHeaderBody(mail.getMessage());

            CompletableFuture<Pair<BlobId, BlobId>> blobIds = CompletableFutureUtil.combine(
                objectStore.save(splitHeaderBody.getLeft()),
                objectStore.save(splitHeaderBody.getRight()),
                Pair::of);

            blobIds.thenCompose(Throwing.function(pair ->
                mailDAO.store(url, mail, pair.getLeft(), pair.getRight())))
                .thenCompose(any -> CompletableFuture.allOf(
                    countDAO.increment(url),
                    keysDAO.store(url, mailKey)))
                .join();
            return mailKey;
        } catch (IOException e) {
            throw new MessagingException("Exception while storing mail", e);
        }
    }

    public Pair<byte[], byte[]> splitHeaderBody(MimeMessage message) throws IOException, MessagingException {
        byte[] messageAsArray = messageToArray(message);
        int bodyStartOctet = computeBodyStartOctet(messageAsArray);

        return Pair.of(
            getHeaderBytes(messageAsArray, bodyStartOctet),
            getBodyBytes(messageAsArray, bodyStartOctet));
    }

    public byte[] messageToArray(MimeMessage message) throws IOException, MessagingException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        message.writeTo(byteArrayOutputStream);
        return byteArrayOutputStream.toByteArray();
    }

    public byte[] getHeaderBytes(byte[] messageContentAsArray, int bodyStartOctet) {
        ByteBuffer headerContent = ByteBuffer.wrap(messageContentAsArray, 0, bodyStartOctet);
        byte[] headerBytes = new byte[bodyStartOctet];
        headerContent.get(headerBytes);
        return headerBytes;
    }

    public byte[] getBodyBytes(byte[] messageContentAsArray, int bodyStartOctet) {
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

    public int computeBodyStartOctet(byte[] messageAsArray) throws IOException {
        try (BodyOffsetInputStream bodyOffsetInputStream =
                 new BodyOffsetInputStream(new ByteArrayInputStream(messageAsArray))) {
            consume(bodyOffsetInputStream);

            if (bodyOffsetInputStream.getBodyStartOffset() == -1) {
                return 0;
            }
            return (int) bodyOffsetInputStream.getBodyStartOffset();
        }
    }

    private void consume(InputStream in) throws IOException {
        IOUtils.copy(in, NULL_OUTPUT_STREAM);
    }

    @Override
    public Iterator<MailKey> list() {
        return keysDAO.list(url)
            .join()
            .iterator();
    }

    @Override
    public Mail retrieve(MailKey key) {
        return CompletableFutureUtil
            .unwrap(mailDAO.read(url, key)
                .thenApply(optional -> optional.map(this::toMail)))
            .join()
            .orElse(null);
    }

    public CompletableFuture<Mail> toMail(CassandraMailRepositoryMailDAO.MailDTO mailDTO) {
        return CompletableFutureUtil.combine(
            objectStore.read(mailDTO.getHeaderBlobId()),
            objectStore.read(mailDTO.getBodyBlobId()),
            Bytes::concat)
            .thenApply(this::toMimeMessage)
            .thenApply(mimeMessage -> mailDTO.getMailBuilder()
                .mimeMessage(mimeMessage)
                .build());
    }

    public MimeMessage toMimeMessage(byte[] bytes) {
        try {
            return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(bytes));
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void remove(Mail mail) {
        removeAsync(MailKey.forMail(mail)).join();
    }

    @Override
    public void remove(Collection<Mail> toRemove) {
        FluentFutureStream.of(toRemove.stream()
            .map(MailKey::forMail)
            .map(this::removeAsync))
            .join();
    }

    @Override
    public void remove(MailKey key) {
        removeAsync(key).join();
    }

    public CompletableFuture<Void> removeAsync(MailKey key) {
        return CompletableFuture.allOf(
            keysDAO.remove(url, key),
            countDAO.decrement(url))
            .thenCompose(any -> mailDAO.remove(url, key));
    }

    @Override
    public long size() throws MessagingException {
        return countDAO.getCount(url).join();
    }

    @Override
    public void removeAll() throws MessagingException {
        keysDAO.list(url)
            .thenCompose(stream -> FluentFutureStream.of(stream.map(this::removeAsync))
                .completableFuture())
            .join();
    }

    @Override
    public boolean lock(MailKey key) {
        return false;
    }

    @Override
    public boolean unlock(MailKey key) {
        return false;
    }
}
