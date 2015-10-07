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

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import org.apache.james.repository.api.ObjectRepository;

/**
 * This is a simple implementation of persistent object store using object
 * serialization on the file system.
 */
public class FilePersistentObjectRepository extends AbstractFileRepository implements ObjectRepository {

    /**
     * @see
     * org.apache.james.repository.file.AbstractFileRepository#getExtensionDecorator()
     */
    protected String getExtensionDecorator() {
        return ".FileObjectStore";
    }

    /**
     * @see
     * org.apache.james.repository.api.ObjectRepository#get(java.lang.String)
     */
    public synchronized Object get(final String key) {
        try {
            final InputStream inputStream = getInputStream(key);

            if (inputStream == null)
                throw new NullPointerException("Null input stream returned for key: " + key);
            try {
                final ObjectInputStream stream = new ObjectInputStream(inputStream);

                if (stream == null)
                    throw new NullPointerException("Null stream returned for key: " + key);

                final Object object = stream.readObject();
                if (DEBUG) {
                    getLogger().debug("returning object " + object + " for key " + key);
                }
                return object;
            } finally {
                inputStream.close();
            }
        } catch (final Throwable e) {
            throw new RuntimeException("Exception caught while retrieving an object, cause: " + e.toString());
        }
    }

    /**
     * @see
     * org.apache.james.repository.api.ObjectRepository#get(java.lang.String,
     * java.lang.ClassLoader)
     */
    public synchronized Object get(final String key, final ClassLoader classLoader) {
        try {
            final InputStream inputStream = getInputStream(key);

            if (inputStream == null)
                throw new NullPointerException("Null input stream returned for key: " + key);

            try {
                final ObjectInputStream stream = new ClassLoaderObjectInputStream(classLoader, inputStream);

                if (stream == null)
                    throw new NullPointerException("Null stream returned for key: " + key);

                final Object object = stream.readObject();

                if (DEBUG) {
                    getLogger().debug("returning object " + object + " for key " + key);
                }
                return object;
            } finally {
                inputStream.close();
            }
        } catch (final Throwable e) {
            throw new RuntimeException("Exception caught while retrieving an object: " + e);
        }

    }

    /**
     * @see
     * org.apache.james.repository.api.ObjectRepository#put(java.lang.String,
     * java.lang.Object)
     */
    public synchronized void put(final String key, final Object value) {
        try {
            final OutputStream outputStream = getOutputStream(key);

            try {
                final ObjectOutputStream stream = new ObjectOutputStream(outputStream);
                stream.writeObject(value);
                if (DEBUG)
                    getLogger().debug("storing object " + value + " for key " + key);
            } finally {
                outputStream.close();
            }
        } catch (final Exception e) {
            throw new RuntimeException("Exception caught while storing an object: " + e);
        }
    }

}
