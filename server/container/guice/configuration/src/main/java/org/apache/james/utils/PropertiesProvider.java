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

package org.apache.james.utils;

import java.io.File;
import java.io.FileNotFoundException;

import javax.inject.Inject;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.james.filesystem.api.FileSystem;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class PropertiesProvider {

    private final FileSystem fileSystem;

    @Inject
    public PropertiesProvider(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public Configuration getConfiguration(String fileName) throws FileNotFoundException, ConfigurationException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(fileName));
        File file = fileSystem.getFile(FileSystem.FILE_PROTOCOL_AND_CONF + fileName + ".properties");
        if (!file.exists()) {
            throw new FileNotFoundException();
        }
        return new PropertiesConfiguration(file);
    }
}