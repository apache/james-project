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

package org.apache.james.blob.export.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

class FileSystemExtensionTest {

    private static ExtensionContext DUMMY_EXTENSION_CONTEXT = null;

    @RegisterExtension
    static FileSystemExtension fileSystemExtension = new FileSystemExtension();

    @Nested
    class DeletingFileSystemBaseDir {

        @Test
        void extensionShouldDeleteWhenTestDoesntCreateNewFiles() throws Exception {
            fileSystemExtension.afterAll(DUMMY_EXTENSION_CONTEXT);

            assertThat(fileSystemExtension.getFileSystem().getBasedir())
                .doesNotExist();
        }

        @Test
        void extensionShouldDeleteWhenTestCreateNewFiles() throws Exception {
            File baseDir = fileSystemExtension.getFileSystem().getBasedir();
            FileUtils.forceMkdir(baseDir);

            File fileInsideBaseDir = new File(baseDir.getPath() + "/fileInsideBaseDir.temp");
            FileUtils.touch(fileInsideBaseDir);

            fileSystemExtension.afterAll(DUMMY_EXTENSION_CONTEXT);

            assertThat(fileSystemExtension.getFileSystem().getBasedir())
                .doesNotExist();
        }
    }
}
