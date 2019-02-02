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

package org.apache.james.mailbox.model;

import java.util.Objects;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.hash.Hashing;

public class BlobId {
    public static BlobId fromBytes(byte[] bytes) {
        Preconditions.checkNotNull(bytes);
        return new BlobId(Hashing.sha256().hashBytes(bytes).toString());
    }

    public static BlobId fromString(String raw) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(raw));
        return new BlobId(raw);
    }

    private final String id;

    private BlobId(String id) {
        this.id = id;
    }

    public String asString() {
        return id;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof BlobId) {
            BlobId blobId = (BlobId) o;

            return Objects.equals(this.id, blobId.id);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("id", id)
            .toString();
    }
}
