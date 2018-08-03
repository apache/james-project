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
import java.io.OutputStream;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.util.Bytes;

/**
 * Provide an {@link OutputStream} which will write to a row. The written data
 * will be split up by chunks of the given chunkSize. Each chunk we get written
 * to own column which will have the chunk number (starting at 0) as column key
 * (Long).
 *
 * This implementation is not thread-safe!
 * Based on Hector implementation for Cassandra.
 * https://github.com/rantav/hector/blob/master/core/src/main/java/me/prettyprint/cassandra/io/ChunkOutputStream.java
 */
public class ChunkOutputStream extends OutputStream {

    private final Configuration conf;
    private final byte[] tableName;
    private final byte[] cf;
    private final byte[] key;
    private final byte[] chunk;
    private long chunkPos = 1;
    private long pos = 0;

    /**
     * Creates a special type of {@link OutputStream} that writes data directly to HBase.
     * @param conf HBase cluster configuration
     * @param tableName name of the table that writes will be made
     * @param cf name of the column family where data is going to be written
     * @param key the row key 
     * @param chunkSize the size of each column, in bytes. For HBase, max is 10MB
     */
    public ChunkOutputStream(Configuration conf, byte[] tableName, byte[] cf, byte[] key, int chunkSize) {
        this.conf = conf;
        this.tableName = tableName;
        this.cf = cf;
        this.key = key;
        this.chunk = new byte[chunkSize];
    }

    @Override
    public void write(int b) throws IOException {
        if (chunk.length - 1 == pos) {
            flush();
        }
        chunk[(int) pos++] = (byte) b;
    }

    @Override
    public void close() throws IOException {
        writeData(true);
    }

    /**
     * Trigger a flush. This will only write the content to the column if the
     * chunk size is reached
     */
    @Override
    public void flush() throws IOException {
        writeData(false);
    }

    /**
     * Write the data to column if the configured chunk size is reached or if the
     * stream should be closed
     *
     * @param close
     * @throws IOException
     */
    private void writeData(boolean close) throws IOException {
        if (pos != 0 && (close || pos == chunk.length - 1)) {
            try (HTable messages = new HTable(conf, tableName)) {
                Put put = new Put(key);
                put.add(cf, Bytes.toBytes(chunkPos), Bytes.head(chunk, (int) pos + 1));
                messages.put(put);
                chunkPos++;
                pos = 0;

            } catch (IOException e) {
                throw new IOException("Unable to write data", e);
            }
        }
    }
}
