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

package org.apache.james.imap.message;

import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.List;
import java.util.Optional;
import java.util.Vector;

/** A {@link Literal} concatenating an ordered sequence of {@link Literal}s, so a whole response fragment (text + literal(s) + text) is emitted in one pass and a single flush. */
public class SequencedLiteral implements Literal {
    private final List<Literal> parts;

    public SequencedLiteral(List<Literal> parts) {
        this.parts = parts;
    }

    public List<Literal> parts() {
        return parts;
    }

    @Override
    public long size() throws IOException {
        long size = 0;
        for (Literal part : parts) {
            size += part.size();
        }
        return size;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        Vector<InputStream> streams = new Vector<>(parts.size());
        for (Literal part : parts) {
            streams.add(part.getInputStream());
        }
        return new SequenceInputStream(streams.elements());
    }

    @Override
    public Optional<byte[][]> asBytesSequence() {
        byte[][][] partsChunks = new byte[parts.size()][][];
        int count = 0;
        for (int i = 0; i < parts.size(); i++) {
            Optional<byte[][]> partChunks = parts.get(i).asBytesSequence();
            if (partChunks.isEmpty()) {
                // A single non-in-memory part forces the whole sequence to be streamed.
                return Optional.empty();
            }
            partsChunks[i] = partChunks.get();
            count += partsChunks[i].length;
        }
        byte[][] chunks = new byte[count][];
        int index = 0;
        for (byte[][] partChunks : partsChunks) {
            for (byte[] chunk : partChunks) {
                chunks[index++] = chunk;
            }
        }
        return Optional.of(chunks);
    }
}
