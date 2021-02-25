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

package org.apache.james.blob.api;

import java.util.Objects;

import org.apache.commons.lang3.NotImplementedException;

import com.google.common.io.ByteSource;

public class TestBlobId implements BlobId {

    public static class Factory implements BlobId.Factory {
        @Override
        public BlobId forPayload(byte[] payload) {
            throw new NotImplementedException("Use from(String) instead");
        }

        @Override
        public BlobId forPayload(ByteSource payload) {
            throw new NotImplementedException("Use from(String) instead");
        }

        @Override
        public BlobId from(String id) {
            return new TestBlobId(id);
        }
    }

    private final String rawValue;

    public TestBlobId(String rawValue) {
        this.rawValue = rawValue;
    }

    @Override
    public String asString() {
        return rawValue;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof TestBlobId) {
            TestBlobId that = (TestBlobId) o;

            return Objects.equals(this.rawValue, that.rawValue);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(rawValue);
    }
}
