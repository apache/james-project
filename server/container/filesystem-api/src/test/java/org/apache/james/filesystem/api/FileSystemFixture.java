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

package org.apache.james.filesystem.api;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class FileSystemFixture {

    public static final FileSystem THROWING_FILE_SYSTEM = new FileSystem() {
        @Override
        public InputStream getResource(String url) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public File getFile(String fileURL) throws FileNotFoundException {
            throw new FileNotFoundException();
        }

        @Override
        public File getBasedir() throws FileNotFoundException {
            throw new UnsupportedOperationException();
        }
    };

    public static final FileSystem CLASSPATH_FILE_SYSTEM = new FileSystem() {
        @Override
        public InputStream getResource(String url) throws IOException {
            return ClassLoader.getSystemResourceAsStream(url);
        }

        @Override
        public File getFile(String fileURL) throws FileNotFoundException {
            return new File(ClassLoader.getSystemResource("recursive/extensions-jars").getFile());
        }

        @Override
        public File getBasedir() throws FileNotFoundException {
            throw new UnsupportedOperationException();
        }
    };

    public static final FileSystem RECURSIVE_CLASSPATH_FILE_SYSTEM = new FileSystem() {
        @Override
        public InputStream getResource(String url) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public File getFile(String fileURL) throws FileNotFoundException {
            return new File(ClassLoader.getSystemResource("recursive/").getFile());
        }

        @Override
        public File getBasedir() throws FileNotFoundException {
            throw new UnsupportedOperationException();
        }
    };
}
