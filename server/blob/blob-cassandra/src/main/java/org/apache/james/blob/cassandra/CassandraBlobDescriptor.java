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

import java.util.Map;

import org.apache.james.blob.api.BlobStoreDAO.BlobMetadata;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadataName;
import org.apache.james.blob.api.BlobStoreDAO.BlobMetadataValue;

import com.google.common.collect.ImmutableMap;

record CassandraBlobDescriptor(int rowCount, BlobMetadata metadata) {
    static CassandraBlobDescriptor from(int rowCount, Map<String, String> metadata) {
        return new CassandraBlobDescriptor(rowCount, toBlobMetadata(metadata));
    }

    static Map<String, String> toRawMetadata(BlobMetadata metadata) {
        return metadata.underlyingMap().entrySet().stream()
            .collect(ImmutableMap.toImmutableMap(
                entry -> entry.getKey().name(),
                entry -> entry.getValue().value()));
    }

    private static BlobMetadata toBlobMetadata(Map<String, String> metadata) {
        return new BlobMetadata(metadata.entrySet().stream()
            .collect(ImmutableMap.toImmutableMap(
                entry -> new BlobMetadataName(entry.getKey()),
                entry -> new BlobMetadataValue(entry.getValue()))));
    }
}
