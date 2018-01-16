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

package org.apache.james.blob.cassandra;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.james.blob.api.BlobId;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class CassandraBlobId implements BlobId {

    public static CassandraBlobId forPayload(byte[] payload) {
        Preconditions.checkArgument(payload != null);
        return new CassandraBlobId(DigestUtils.sha256Hex(payload));
    }

    public static CassandraBlobId from(String id) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(id));
        return new CassandraBlobId(id);
    }

    private final String id;

    @VisibleForTesting
    CassandraBlobId(String id) {
        this.id = id;
    }

    @Override
    public String asString() {
        return id;
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof CassandraBlobId) {
            CassandraBlobId other = (CassandraBlobId) obj;
            return Objects.equal(id, other.id);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return MoreObjects
            .toStringHelper(this)
            .add("id", id)
            .toString();
    }
}
