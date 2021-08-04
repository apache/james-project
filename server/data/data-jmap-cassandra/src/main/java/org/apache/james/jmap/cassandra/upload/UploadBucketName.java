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

package org.apache.james.jmap.cassandra.upload;

import java.util.Optional;

import org.apache.james.blob.api.BucketName;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class UploadBucketName {
    private static final String PREFIX = "uploads-";

    public static Optional<UploadBucketName> ofBucket(BucketName bucketName) {
        String bucketNameString = bucketName.asString();
        if (!bucketNameString.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String weekPart = bucketNameString.substring(PREFIX.length());
        try {
            int weekNumber = Integer.parseInt(weekPart);
            if (weekNumber >= 0) {
                return Optional.of(new UploadBucketName(weekNumber));
            } else {
                return Optional.empty();
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private final int weekNumber;

    public UploadBucketName(int weekNumber) {
        Preconditions.checkArgument(weekNumber >= 0, "'weekNumber' should be strictly positive");

        this.weekNumber = weekNumber;
    }

    public BucketName asBucketName() {
        return BucketName.of(String.format(PREFIX + "%d", weekNumber));
    }

    public int getWeekNumber() {
        return weekNumber;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof UploadBucketName) {
            UploadBucketName other = (UploadBucketName) obj;
            return Objects.equal(weekNumber, other.weekNumber);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(weekNumber);
    }

    @Override
    public String toString() {
        return MoreObjects
            .toStringHelper(this)
            .add("weekNumber", weekNumber)
            .toString();
    }
}
