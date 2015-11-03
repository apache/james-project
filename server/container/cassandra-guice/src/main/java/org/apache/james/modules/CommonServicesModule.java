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

package org.apache.james.modules;

import java.util.Optional;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.cli.MissingArgumentException;
import org.apache.james.core.JamesServerResourceLoader;
import org.apache.james.core.filesystem.FileSystemImpl;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.filesystem.api.JamesDirectoriesProvider;
import org.apache.james.utils.ConfigurationProvider;
import org.apache.james.utils.FileConfigurationProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class CommonServicesModule extends AbstractModule {
    
    public static final String CONFIGURATION_PATH = "configurationPath";
    
    @Override
    protected void configure() {
        bind(FileSystem.class).to(FileSystemImpl.class);
        bind(ConfigurationProvider.class).to(FileConfigurationProvider.class);
    }

    @Provides @Singleton @Named(CONFIGURATION_PATH)
    public String configurationPath() {
        return FileSystem.FILE_PROTOCOL_AND_CONF;
    }
    
    @Provides @Singleton
    public JamesDirectoriesProvider directories() throws MissingArgumentException {
        String rootDirectory = Optional
                .ofNullable(System.getProperty("working.directory"))
                .orElseThrow(() -> new MissingArgumentException("Server needs a working.directory env entry"));
        return new JamesServerResourceLoader(rootDirectory);
    }
    
}
