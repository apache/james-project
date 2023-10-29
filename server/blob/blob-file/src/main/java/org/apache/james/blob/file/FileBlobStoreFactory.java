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

package org.apache.james.blob.file;

import java.io.FileNotFoundException;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.server.blob.deduplication.BlobStoreFactory;

public class FileBlobStoreFactory {
    private final FileSystem fileSystem;

    public FileBlobStoreFactory(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public BlobStoreFactory.RequireBlobIdFactory builder() {
        return blobIdFactory -> {
            try {
                return BlobStoreFactory.builder()
                    .blobStoreDAO(new FileBlobStoreDAO(fileSystem, blobIdFactory))
                    .blobIdFactory(blobIdFactory);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        };
    }
}
