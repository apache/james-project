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

package org.apache.james.mailbox.cassandra.ids;

import org.apache.commons.codec.digest.DigestUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class BlobId {
    public static BlobId forPayload(byte[] payload) {
        Preconditions.checkArgument(payload != null);
        return new BlobId(DigestUtils.sha1Hex(payload));
    }

    public static BlobId from(String id) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(id));
        return new BlobId(id);
    }

    private final String id;

    @VisibleForTesting
    BlobId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof BlobId) {
            BlobId other = (BlobId) obj;
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
