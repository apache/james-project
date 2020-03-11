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

package org.apache.james.jmap.draft.utils;

import java.util.Optional;

public class DownloadPath {
    public static DownloadPath ofBlobId(String blobId) {
        return new DownloadPath(blobId, Optional.empty());
    }

    public static DownloadPath of(String blobId, String name) {
        return new DownloadPath(blobId, Optional.of(name));
    }

    private final String blobId;
    private final Optional<String> name;

    private DownloadPath(String blobId, Optional<String> name) {
        this.blobId = blobId;
        this.name = name;
    }

    public String getBlobId() {
        return blobId;
    }

    public Optional<String> getName() {
        return name;
    }
}
