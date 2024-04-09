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
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.filesystem.api.JamesDirectoriesProvider;

public class ResourceFactory {

    private final JamesDirectoriesProvider directoryProvider;

    public ResourceFactory(JamesDirectoriesProvider directoryProvider) {
        this.directoryProvider = directoryProvider;
    }

    public void validate(File file) throws IOException {
        Path resourcePath = file.toPath().normalize();
        if (!resourcePath.startsWith(Paths.get(directoryProvider.getConfDirectory()).normalize())
            && !resourcePath.startsWith(Paths.get(directoryProvider.getRootDirectory()).normalize())
            && !resourcePath.startsWith(Paths.get(directoryProvider.getVarDirectory()).normalize())) {

            throw new IOException(String.format("%s path is not part of allowed resource locations: %s, %s, %s",
                resourcePath.toFile().getCanonicalPath(), directoryProvider.getConfDirectory(), directoryProvider.getRootDirectory(),
                directoryProvider.getVarDirectory()));
        }
    }
    
    public Resource getResource(String fileURL) {
        if (fileURL.startsWith(FileSystem.CLASSPATH_PROTOCOL)) {
            return handleClasspathProtocol(fileURL);
        } else if (fileURL.startsWith(FileSystem.FILE_PROTOCOL)) {
            return handleFileProtocol(fileURL);
        } else {
            try {
                // Try to parse the location as a URL...
                return handleUrlResource(fileURL);
            } catch (MalformedURLException | URISyntaxException ex) {
                // No URL -> resolve as resource path.
                return new ClassPathResource(fileURL);
            }
        }
    }

    private Resource handleUrlResource(String fileURL) throws MalformedURLException, URISyntaxException {
        URL url = new URI(fileURL).toURL();
        return new UrlResource(url);
    }

    private Resource handleClasspathProtocol(String fileURL) {
        String resourceName = fileURL.substring(FileSystem.CLASSPATH_PROTOCOL.length());
        return new ClassPathResource(resourceName);
    }
    
    private Resource handleFileProtocol(String fileURL) {
        File file = interpretPath(fileURL);
        return new FileSystemResource(file);
    }

    private File interpretPath(String fileURL) {
        if (FileProtocol.CONF.match(fileURL)) {
            return new File(directoryProvider.getConfDirectory() + "/" + FileProtocol.CONF.removeProtocolFromPath(fileURL));
        } else if (FileProtocol.VAR.match(fileURL)) {
            return new File(directoryProvider.getVarDirectory() + "/" + FileProtocol.VAR.removeProtocolFromPath(fileURL));
        } else if (FileProtocol.ABSOLUTE.match(fileURL)) {
            return new File(directoryProvider.getAbsoluteDirectory() + FileProtocol.ABSOLUTE.removeProtocolFromPath(fileURL));
        } else {
            // move to the root folder of the spring deployment
            return new File(directoryProvider.getRootDirectory() + "/" + FileProtocol.OTHER.removeProtocolFromPath(fileURL));
        }
    }
    
    private enum FileProtocol {
        CONF(FileSystem.FILE_PROTOCOL_AND_CONF),
        VAR(FileSystem.FILE_PROTOCOL_AND_VAR),
        ABSOLUTE(FileSystem.FILE_PROTOCOL_ABSOLUTE),
        OTHER(FileSystem.FILE_PROTOCOL);
        
        private final String protocolPrefix;
        
        FileProtocol(String protocolPrefix) {
            this.protocolPrefix = protocolPrefix;
        }
        
        private boolean match(String path) {
            return path.startsWith(protocolPrefix);
        }
        
        private String removeProtocolFromPath(String path) {
            return path.substring(protocolPrefix.length());
        }
    }
}