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

package org.apache.james.blob.aes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.apache.commons.io.IOUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.ObjectStoreIOException;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.subtle.AesGcmJce;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class AESBlobStoreDAO implements BlobStoreDAO {
    private static final byte[] EMPTY_ASSOCIATED_DATA = new byte[0];
    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int KEY_SIZE = 256;
    private static final String SECRET_KEY_FACTORY_ALGORITHM = "PBKDF2WithHmacSHA256";

    private final BlobStoreDAO underlying;
    private final Aead aead;

    public AESBlobStoreDAO(BlobStoreDAO underlying, CryptoConfig cryptoConfig) {
        this.underlying = underlying;

        try {
            AeadConfig.register();

            SecretKey secretKey = deriveKey(cryptoConfig);
            aead = new AesGcmJce(secretKey.getEncoded());
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Error while starting AESPayloadCodec", e);
        }
    }

    private static SecretKey deriveKey(CryptoConfig cryptoConfig) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] saltBytes = cryptoConfig.salt();
        SecretKeyFactory skf = SecretKeyFactory.getInstance(SECRET_KEY_FACTORY_ALGORITHM);
        PBEKeySpec spec = new PBEKeySpec(cryptoConfig.password(), saltBytes, PBKDF2_ITERATIONS, KEY_SIZE);
        return skf.generateSecret(spec);
    }

    public byte[] encrypt(byte[] input) {
        try {
            return aead.encrypt(input, EMPTY_ASSOCIATED_DATA);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Unable to build payload for object storage, failed to encrypt", e);
        }
    }

    public byte[] decrypt(byte[] ciphertext) throws IOException {
        try {
            return aead.decrypt(ciphertext, EMPTY_ASSOCIATED_DATA);
        } catch (GeneralSecurityException e) {
            throw new IOException("Incorrect crypto setup", e);
        }
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) throws ObjectStoreIOException, ObjectNotFoundException {
        return Mono.from(underlying.readBytes(bucketName, blobId))
            .map(Throwing.function(this::decrypt))
            .map(ByteArrayInputStream::new)
            .subscribeOn(Schedulers.elastic())
            .block();
    }

    @Override
    public Publisher<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        return Mono.from(underlying.readBytes(bucketName, blobId))
            .map(Throwing.function(this::decrypt));
    }

    @Override
    public Publisher<Void> save(BucketName bucketName, BlobId blobId, byte[] data) {
        Preconditions.checkNotNull(bucketName);
        Preconditions.checkNotNull(blobId);
        Preconditions.checkNotNull(data);

        return Mono.just(data)
            .flatMap(payload -> Mono.fromCallable(() -> encrypt(payload)).subscribeOn(Schedulers.parallel()))
            .flatMap(encryptedPayload -> Mono.from(underlying.save(bucketName, blobId, encryptedPayload)))
            .onErrorMap(e -> new ObjectStoreIOException("Exception occurred while saving bytearray", e));
    }

    @Override
    public Publisher<Void> save(BucketName bucketName, BlobId blobId, InputStream inputStream) {
        Preconditions.checkNotNull(bucketName);
        Preconditions.checkNotNull(blobId);
        Preconditions.checkNotNull(inputStream);

        return Mono.just(inputStream)
            .flatMap(data -> Mono.fromCallable(() -> IOUtils.toByteArray(inputStream)).subscribeOn(Schedulers.parallel()))
            .flatMap(encryptedData -> Mono.from(save(bucketName, blobId, encryptedData)))
            .onErrorMap(e -> new ObjectStoreIOException("Exception occurred while saving bytearray", e));
    }

    @Override
    public Publisher<Void> save(BucketName bucketName, BlobId blobId, ByteSource content) {
        Preconditions.checkNotNull(bucketName);
        Preconditions.checkNotNull(blobId);
        Preconditions.checkNotNull(content);

        return Mono.using(content::openStream,
            in -> Mono.from(save(bucketName, blobId, in)),
            Throwing.consumer(InputStream::close));
    }

    @Override
    public Publisher<Void> delete(BucketName bucketName, BlobId blobId) {
        return underlying.delete(bucketName, blobId);
    }

    @Override
    public Publisher<Void> deleteBucket(BucketName bucketName) {
        return underlying.deleteBucket(bucketName);
    }
}
