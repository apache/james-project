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

package org.apache.james.blob.objectstorage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.jclouds.io.Payloads;

public class DefaultPayloadCodec implements PayloadCodec {
    @Override
    public Payload write(InputStream is) {
        return new Payload(Payloads.newInputStreamPayload(is), Optional.empty());
    }

    @Override
    public Payload write(byte[] bytes) {
        if (bytes.length == 0) {
            return write(new ByteArrayInputStream(bytes));
        }
        return new Payload(
            Payloads.newByteArrayPayload(bytes),
            Optional.of(Integer.valueOf(bytes.length).longValue()));
    }

    @Override
    public InputStream read(Payload payload) throws IOException {
        return payload.getPayload().openStream();
    }
}
