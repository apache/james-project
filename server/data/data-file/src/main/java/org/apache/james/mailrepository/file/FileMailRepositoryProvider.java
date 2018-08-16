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

package org.apache.james.mailrepository.file;

import javax.inject.Inject;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryProvider;
import org.apache.james.mailrepository.api.MailRepositoryUrl;

public class FileMailRepositoryProvider implements MailRepositoryProvider {

    private final FileSystem fileSystem;

    @Inject
    public FileMailRepositoryProvider(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Override
    public String canonicalName() {
        return FileMailRepository.class.getCanonicalName();
    }

    @Override
    public MailRepository provide(MailRepositoryUrl url) {
        FileMailRepository fileMailRepository = new FileMailRepository();
        fileMailRepository.setFileSystem(fileSystem);
        return fileMailRepository;
    }
}
