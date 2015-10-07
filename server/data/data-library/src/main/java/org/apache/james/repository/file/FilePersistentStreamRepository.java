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


package org.apache.james.repository.file;

import org.apache.james.repository.api.StreamRepository;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Implementation of a StreamRepository to a File.<br>
 * TODO: -retieve(String key) should return a FilterInputStream to allow
 * mark and reset methods. (working not like BufferedInputStream!!!)
 */
public class FilePersistentStreamRepository extends AbstractFileRepository implements StreamRepository {

    @Override
    protected String getExtensionDecorator() {
        return ".FileStreamStore";
    }


    @Override
    public synchronized InputStream get(final String key) {
        try {
            return getInputStream(key);
        } catch (final IOException ioe) {
            final String message = "Exception caught while retrieving a stream ";
            getLogger().warn(message, ioe);
            throw new RuntimeException(message + ": " + ioe);
        }
    }


    @Override
    public synchronized OutputStream put(final String key) {
        try {
            final OutputStream outputStream = getOutputStream(key);
            return new BufferedOutputStream(outputStream);
        } catch (final IOException ioe) {
            final String message = "Exception caught while storing a stream ";
            getLogger().warn(message, ioe);
            throw new RuntimeException(message + ": " + ioe);
        }
    }

    /**
     * Return the size of the file which belongs to the given key
     *
     * @param key the key to get the size for
     * @return size the Size which belongs to the givens keys file
     */
    public long getSize(final String key) {
        try {
            return getFile(key).length();
        } catch (IOException e) {
            return 0;
        }
    }
}
