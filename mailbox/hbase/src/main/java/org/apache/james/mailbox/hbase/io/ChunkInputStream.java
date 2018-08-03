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
package org.apache.james.mailbox.hbase.io;

import java.io.IOException;
import java.io.InputStream;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;

/**
 * Return an InputStream which retrieve columns from a row which stores chunk of
 * data. See also {@link ChunkOutputStream}
 *
 * This implementation is not thread-safe!
 * 
 * Bsed on Hector implementation for Cassandra.
 * https://github.com/rantav/hector/blob/master/core/src/main/java/me/prettyprint/cassandra/io/ChunkInputStream.java
 */
public class ChunkInputStream extends InputStream {

    private final Configuration conf;
    private final byte[] tableName;
    private final byte[] cf;
    private final byte[] key;
    private int pos;
    private long chunkPos = 1;
    private byte[] chunk;

    public ChunkInputStream(Configuration conf, byte[] tableName, byte[] cf, byte[] key) {
        this.key = key;
        this.conf = conf;
        this.tableName = tableName;
        this.cf = cf;
    }

    public ChunkInputStream(Configuration conf, String tableName, String cf, byte[] key) {
        this(conf, Bytes.toBytes(tableName), Bytes.toBytes(cf), key);
    }

    @Override
    public int read() throws IOException {
        if (chunk == null || pos + 1 == chunk.length) {
            if (!fetchChunk()) {
                return -1;
            }
        }
        return chunk[pos++];
    }

    /**
     * Fetch the next chunk.
     *
     * @return exists if there was a chunk to fetch.
     * @throws IOException
     */
    private boolean fetchChunk() throws IOException {
        try (HTable messages = new HTable(conf, tableName)) {
            byte[] cp = Bytes.toBytes(chunkPos);
            Get get = new Get(key);
            get.addColumn(cf, cp);
            get.setMaxVersions(1);
            Result result = messages.get(get);
            if (!result.isEmpty()) {
                chunk = result.getValue(cf, cp);
                chunkPos++;
                pos = 0;
                return true;
            } else {
                return false;
            }
        } catch (IOException e) {
            throw new IOException("Unable to read data", e);
        }
    }

    @Override
    public boolean markSupported() {
        return false;
    }
}
