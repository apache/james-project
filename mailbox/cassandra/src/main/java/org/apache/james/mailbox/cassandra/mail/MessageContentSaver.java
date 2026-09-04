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

package org.apache.james.mailbox.cassandra.mail;

import org.apache.james.blob.api.BlobId;

import com.google.common.io.ByteSource;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

/**
 * Saves the content of a message: its headers and its body.
 *
 * Implementations decide which recovery policy, if any, is applied alongside the content write.
 */
public interface MessageContentSaver {
    /**
     * @return the blob id of the headers (T1) and the blob id of the body (T2).
     */
    Mono<Tuple2<BlobId, BlobId>> saveContent(byte[] headerBytes, ByteSource bodyByteSource);
}
