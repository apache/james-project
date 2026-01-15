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

package org.apache.james.vault.blob;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.inject.Inject;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.PlainBlobId;

public class BlobIdTimeGenerator {
    public static final String BLOB_ID_GENERATING_FORMAT = "%d/%02d/%s";
    public static final Pattern BLOB_ID_TIME_PATTERN = Pattern.compile("^(\\d{4})/(\\d{2})/(.*)$");

    private final BlobId.Factory blobIdFactory;
    private final Clock clock;

    @Inject
    public BlobIdTimeGenerator(BlobId.Factory blobIdFactory, Clock clock) {
        this.blobIdFactory = blobIdFactory;
        this.clock = clock;
    }

    BlobId currentBlobId() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        int month = now.getMonthValue();
        int year = now.getYear();

        return new PlainBlobId(String.format(BLOB_ID_GENERATING_FORMAT, year, month, blobIdFactory.of(UUID.randomUUID().toString()).asString()));
    }

    public BlobId toDeletedMessageBlobId(String blobId) {
        return Optional.of(BLOB_ID_TIME_PATTERN.matcher(blobId))
            .filter(Matcher::matches)
            .map(matcher -> {
                int year = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                String subBlobId = matcher.group(3);
                return (BlobId) new PlainBlobId(String.format(BLOB_ID_GENERATING_FORMAT, year, month, blobIdFactory.parse(subBlobId).asString()));
            }).orElseGet(() -> blobIdFactory.parse(blobId));
    }
}
