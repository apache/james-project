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

package org.apache.james;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.james.server.core.configuration.Configuration;

import com.google.common.collect.ImmutableList;

public class TemporaryJamesServer {
    private static final List<String> CONFIGURATION_FILE_NAMES = ImmutableList.of(
        "dnsservice.xml",
        "domainlist.xml",
        "imapserver.xml",
        "keystore",
        "listeners.xml",
        "lmtpserver.xml",
        "mailetcontainer.xml",
        "mailrepositorystore.xml",
        "managesieveserver.xml",
        "pop3server.xml",
        "smtpserver.xml",
        "usersrepository.xml");

    private final Configuration configuration;
    private final File configurationFolder;
    private final List<String> configurationFileNames;
    private GuiceJamesServer jamesServer;

    public TemporaryJamesServer(File workingDir) {
        this(workingDir, CONFIGURATION_FILE_NAMES);
    }

    public TemporaryJamesServer(File workingDir, List<String> configurationFileNames) {
        this.configurationFileNames = configurationFileNames;
        Configuration configuration = Configuration.builder().workingDirectory(workingDir).build();
        configurationFolder = workingDir.toPath().resolve("conf").toFile();
        if (!configurationFolder.exists()) {
            configurationFolder.mkdir();
        }
        copyResources(Paths.get(configurationFolder.getAbsolutePath()));
        this.configuration = configuration;
    }

    public GuiceJamesServer getJamesServer() {
        if (jamesServer == null) {
            jamesServer = GuiceJamesServer.forConfiguration(configuration);
        }
        return jamesServer;
    }

    private void copyResources(Path resourcesFolder) {
        configurationFileNames
            .forEach(resourceName -> copyResource(resourcesFolder, resourceName, resourceName));
    }

    public void copyResources(String resourceName, String targetName) {
        copyResource(Paths.get(configurationFolder.getAbsolutePath()), resourceName, targetName);
    }

    public static void copyResource(Path resourcesFolder, String resourceName, String targetName) {
        var resolvedResource = resourcesFolder.resolve(targetName);
        try (OutputStream outputStream = new FileOutputStream(resolvedResource.toFile())) {
            URL resource = ClassLoader.getSystemClassLoader().getResource(resourceName);
            if (resource != null) {
                try (InputStream stream = resource.openStream()) {
                    stream.transferTo(outputStream);
                }
            } else {
                throw new RuntimeException("Failed to load configuration resource " + resourceName);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
