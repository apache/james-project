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

package org.apache.james.transport.mailets;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.jsieve.mailet.ResourceLocator;

/**
 * To maintain backwards compatibility with existing installations, this uses
 * the old file based scheme.
 * <p> The scripts are stored in the <code>sieve</code> sub directory of the application
 * installation directory.
 */
public class ResourceLocatorImpl implements ResourceLocator {

    private final boolean virtualHosting;
    
    private FileSystem fileSystem = null;

    public ResourceLocatorImpl(boolean virtualHosting, FileSystem fileSystem) {
        this.virtualHosting = virtualHosting;
            this.fileSystem = fileSystem;
    }

    public InputStream get(String uri) throws IOException {
        // Use the complete email address for finding the sieve file
        uri = uri.substring(2);

        String username;
        if (virtualHosting) {
            username = uri.substring(0, uri.indexOf("/"));
        } else {
            username = uri.substring(0, uri.indexOf("@"));
        }

        // RFC 5228 permits extensions: .siv .sieve
        String sieveFilePrefix = FileSystem.FILE_PROTOCOL + "sieve/" + username + ".";
        File sieveFile;
        try {
            sieveFile = fileSystem.getFile(sieveFilePrefix + "sieve");
        } catch (FileNotFoundException ex) {
            sieveFile = fileSystem.getFile(sieveFilePrefix + "siv");
        }
        return new FileInputStream(sieveFile);
    }

}
