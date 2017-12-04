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

package org.apache.james.mailets;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Supplier;

import org.apache.commons.io.IOUtils;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.filesystem.api.JamesDirectoriesProvider;
import org.apache.james.modules.CommonServicesModule;
import org.apache.james.server.core.JamesServerResourceLoader;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class TemporaryFilesystemModule extends AbstractModule {

    private static final List<String> CONFIGURATION_FILE_NAMES = ImmutableList.of("dnsservice.xml",
            "domainlist.xml",
            "imapserver.xml",
            "keystore",
            "lmtpserver.xml",
            "mailrepositorystore.xml",
            "managesieveserver.xml",
            "pop3server.xml",
            "recipientrewritetable.xml",
            "usersrepository.xml",
            "smime.p12");

    private final Supplier<File> workingDirectory;

    private static File rootDir(TemporaryFolder temporaryFolder) {
        return temporaryFolder.getRoot();
    }

    public TemporaryFilesystemModule(TemporaryFolder temporaryFolder) {
        this(() -> TemporaryFilesystemModule.rootDir(temporaryFolder));
    }

    public TemporaryFilesystemModule(Supplier<File> workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    @Override
    protected void configure() {
        try {
            bind(JamesDirectoriesProvider.class).toInstance(new JamesServerResourceLoader(workingDirectory.get().getAbsolutePath()));
            copyResources(Paths.get(workingDirectory.get().getAbsolutePath(), "conf"));
            bindConstant().annotatedWith(Names.named(CommonServicesModule.CONFIGURATION_PATH)).to(FileSystem.FILE_PROTOCOL_AND_CONF);
        } catch (IOException e) {
            Throwables.propagate(e);
        }
    }

    private void copyResources(Path resourcesFolder) throws FileNotFoundException, IOException {
        CONFIGURATION_FILE_NAMES
            .forEach(resourceName -> copyResource(resourcesFolder, resourceName));
    }

    private void copyResource(Path resourcesFolder, String resourceName) {
        try (OutputStream outputStream = new FileOutputStream(resourcesFolder.resolve(resourceName).toFile())){
            IOUtils.copy(ClassLoader.getSystemClassLoader().getResource(resourceName).openStream(), outputStream);
        } catch (IOException e) {
            Throwables.propagate(e);
        }
    }
}
