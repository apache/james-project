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
package org.apache.james.server.core.filesystem;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.filesystem.api.JamesDirectoriesProvider;
import org.apache.james.server.core.JamesServerResourceLoader;
import org.apache.james.server.core.configuration.Configuration;

public class FileSystemImpl implements FileSystem {

    public static FileSystemImpl forTesting() {
        return new FileSystemImpl(new JamesServerResourceLoader("../testsFileSystemExtension/" + UUID.randomUUID()));
    }

    public static FileSystemImpl forTestingWithConfigurationFromClasspath() {
        return new FileSystemImpl(Configuration.builder()
            .workingDirectory("../")
            .configurationFromClasspath()
            .build()
            .directories());
    }

    private final JamesDirectoriesProvider directoryProvider;
    private final ResourceFactory resourceLoader;

    public FileSystemImpl(JamesDirectoriesProvider directoryProvider) {
        this.directoryProvider = directoryProvider;
        this.resourceLoader = new ResourceFactory(directoryProvider);
    }

    @Override
    public File getBasedir() throws FileNotFoundException {
        return new File(directoryProvider.getRootDirectory());
    }

    @Override
    public InputStream getResource(String url) throws IOException {
        return resourceLoader.getResource(url).getInputStream();
    }

    @Override
    public File getFile(String fileURL) throws FileNotFoundException {
        try {
            return resourceLoader.getResource(fileURL).getFile();
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override
    public File getFileWithinBaseDir(String fileURL) throws FileNotFoundException, IOException {
        final File file = getFile(fileURL);
        resourceLoader.validate(file);
        return file;
    }
}
