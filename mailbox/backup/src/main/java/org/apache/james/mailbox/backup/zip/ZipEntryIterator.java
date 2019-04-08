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
package org.apache.james.mailbox.backup.zip;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZipEntryIterator implements Iterator<ZipEntry>, Closeable {
    private final ZipInputStream zipInputStream;
    private Optional<ZipEntry> next;

    private static final Logger LOGGER = LoggerFactory.getLogger(ZipEntryIterator.class);

    public ZipEntryIterator(ZipInputStream inputStream) {
        zipInputStream = inputStream;
        try {
            next = Optional.ofNullable(zipInputStream.getNextEntry());
        } catch (IOException e) {
            //EMPTY STREAM
            next = Optional.empty();
        }
    }

    @Override
    public boolean hasNext() {
        return next.isPresent();
    }

    @Override
    public ZipEntry next() {
        Optional<ZipEntry> current = next;
        if (!current.isPresent()) {
            return null;
        }

        ZipEntry currentEntry = current.get();

        advanceToNextEntry();

        return currentEntry;
    }

    private void advanceToNextEntry() {
        try {
            next = Optional.ofNullable(zipInputStream.getNextEntry());
        } catch (IOException e) {
            LOGGER.error("Error when reading archive", e);
            next = Optional.empty();
        }
    }

    @Override
    public void close() throws IOException {
        zipInputStream.close();
    }

}
