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
package org.apache.james.filesystem.api.mock;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.james.filesystem.api.FileSystem;
import org.junit.rules.TemporaryFolder;

public class MockFileSystem implements FileSystem {

    private final TemporaryFolder temporaryFolder;

    public MockFileSystem() throws IOException {
        this.temporaryFolder = new TemporaryFolder();
        temporaryFolder.create();
    }

    @Override
    public File getBasedir() throws FileNotFoundException {
        return temporaryFolder.getRoot();
    }

    @Override
    public InputStream getResource(String url) throws IOException {
        return new FileInputStream(getFile(url));
    }

    @Override
    public File getFile(String fileURL) throws FileNotFoundException {
        try {
            if (fileURL.startsWith("file://")) {
                if (fileURL.startsWith("file://conf/")) {
                    URL url = MockFileSystem.class.getClassLoader().getResource("./" + fileURL.substring(12));
                    try {
                        return new File(new URI(url.toString()));
                    } catch (URISyntaxException e) {
                        throw new FileNotFoundException("Unable to load file");
                    }
                    // return new File("./src"+fileURL.substring(6));
                } else {
                    return new File(temporaryFolder.getRoot() + File.separator + fileURL.substring(FileSystem.FILE_PROTOCOL.length()));
                }
            } else {
                throw new UnsupportedOperationException("getFile: " + fileURL);
            }
        } catch (NullPointerException npe) {
            throw new FileNotFoundException("NPE on: " + fileURL);
        }
    }

    public void clear() {
        temporaryFolder.delete();
    }
}
