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
import java.util.UUID;

import jakarta.inject.Inject;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.PlainBlobId;

public class BlobIdTimeGenerator {
    public static final String BLOB_ID_GENERATING_FORMAT = "%d/%02d/%s/%s/%s";

    private final Clock clock;

    @Inject
    public BlobIdTimeGenerator(Clock clock) {
        this.clock = clock;
    }

    BlobId currentBlobId() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        int month = now.getMonthValue();
        int year = now.getYear();
        String randomizerA = RandomStringUtils.insecure().nextAlphanumeric(1);
        String randomizerB = RandomStringUtils.insecure().nextAlphanumeric(1);

        return new PlainBlobId(String.format(BLOB_ID_GENERATING_FORMAT, year, month, randomizerA, randomizerB, UUID.randomUUID()));
    }
}
