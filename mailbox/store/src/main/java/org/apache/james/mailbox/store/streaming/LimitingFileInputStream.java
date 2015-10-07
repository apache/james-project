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
package org.apache.james.mailbox.store.streaming;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class LimitingFileInputStream extends FileInputStream{
    private long pos = 0;
    private final long limit;
    
    public LimitingFileInputStream(File file, long limit) throws FileNotFoundException {
        super(file);
        this.limit = limit;
    }

    public LimitingFileInputStream(FileDescriptor fdObj, long limit) {
        super(fdObj);
        this.limit = limit;

    }

    public LimitingFileInputStream(String name, long limit) throws FileNotFoundException {
        super(name);
        this.limit = limit;

    }

    @Override
    public int read() throws IOException {
        if (pos >= limit) {
            return -1;
        }
        pos++;
        return super.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (pos >= limit) {
            return -1;
        }
        int readLimit;
        if (pos + len >= limit) {
            readLimit = (int) limit - (int) pos;
        } else {
            readLimit = len;
        }

        int i = super.read(b, off, readLimit);
        pos += i;
        return i;
    }

    @Override
    public long skip(long n) throws IOException {
        long currentPos = pos;
        long i = super.skip(n);
        if (currentPos == pos) {
            pos += i;
        }
        return i;
    }

    @Override
    public int available() throws IOException {
        int i = super.available();
        if (i == -1) {
            return -1;
        } else {
            if (i >= limit) {
                return (int) limit - (int) pos;
            } else {
                return i;
            }
        }
    }
    
    
    /**
     * Return the limit 
     * 
     * @return limit
     */
    public long getLimit() {
        return limit;
    }

    /**
     * Return a READ-ONLY {@link FileChannel} which is limited also in the size
     * 
     * @return channel
     */
    @Override
    public FileChannel getChannel() {
        return new LimitingFileChannel(super.getChannel());
    }
    
    
    /**
     * A {@link FileChannel} implementation which wrapps another {@link FileChannel} and limit the access to it
     * 
     *
     */
    private final class LimitingFileChannel extends FileChannel {

        private final FileChannel channel;

        public LimitingFileChannel(FileChannel channel) {
            this.channel = channel;
        }
        
        @Override
        public int read(ByteBuffer dst) throws IOException {
            int bufLimit = dst.limit();
            int left = (int) bytesLeft();
            int r;
            if (bufLimit > left) {
                dst.limit(left);
                r = channel.read(dst);
                dst.limit(bufLimit);
            } else {
                r = channel.read(dst);
            }
            return r;
            
        }

        @Override
        public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
            long r = 0;
            for (int a = offset; a < length; a++) {
                r += read(dsts[a]);
            }
            
            return r;
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            throw new IOException("Read-Only FileChannel");
        }

        @Override
        public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
            throw new IOException("Read-Only FileChannel");
        }

        @Override
        public long position() throws IOException {
            return channel.position();
        }

        @Override
        public FileChannel position(long newPosition) throws IOException {
            if (newPosition <= limit) {
                channel.position(newPosition);
            }
            return LimitingFileChannel.this ;
        }

        @Override
        public long size() throws IOException {
            return limit;
        }

        @Override
        public FileChannel truncate(long size) throws IOException {
            throw new IOException("Read-Only FileChannel");
        }

        @Override
        public void force(boolean metaData) throws IOException {
            channel.force(metaData);
        }

        @Override
        public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
            if (position > limit) {
                return 0;
            } else {
                long left = bytesLeft();
                
                if (count > left) {
                    count = left;
                }
                return channel.transferTo(position, count, target);
            }
        }

        @Override
        public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
            throw new IOException("Read-Only FileChannel");
        }

        @Override
        public int read(ByteBuffer dst, long position) throws IOException {
            if (position > size()) {
                return 0;
            }
            int bufLimit = dst.limit();
            int left = (int) bytesLeft();
            int r;
            if (bufLimit > left) {
                dst.limit(left);
                r = channel.read(dst, position);
                dst.limit(bufLimit);
            } else {
                r = channel.read(dst, position);
            }
            return r;
        }

        @Override
        public int write(ByteBuffer src, long position) throws IOException {
            throw new IOException("Read-Only FileChannel");

        }

        @Override
        public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
            return channel.map(mode, position, size);
        }

        @Override
        public FileLock lock(long position, long size, boolean shared) throws IOException {
            return channel.lock(position, size, shared);
        }

        @Override
        public FileLock tryLock(long position, long size, boolean shared) throws IOException {
            return channel.tryLock(position, size, shared);
        }

        @Override
        protected void implCloseChannel() throws IOException {
            channel.close();
        }
        
        private long bytesLeft() throws IOException {
            return limit - position();
        }
    }
    

}
