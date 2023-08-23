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

package org.apache.james.jmap.api.upload;

import java.io.InputStream;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.Upload;
import org.apache.james.jmap.api.model.UploadId;
import org.apache.james.jmap.api.model.UploadMetaData;
import org.apache.james.mailbox.model.ContentType;
import org.reactivestreams.Publisher;

public class UploadServiceDefaultImpl implements UploadService {

    private final UploadRepository repository;

    @Inject
    @Singleton
    public UploadServiceDefaultImpl(UploadRepository repository) {
        this.repository = repository;
    }

    @Override
    public Publisher<UploadMetaData> upload(InputStream data, ContentType contentType, Username user) {
        return repository.upload(data, contentType, user);
    }

    @Override
    public Publisher<Upload> retrieve(UploadId id, Username user) {
        return repository.retrieve(id, user);
    }
}
