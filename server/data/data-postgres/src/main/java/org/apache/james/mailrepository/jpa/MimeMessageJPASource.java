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

package org.apache.james.mailrepository.jpa;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.james.server.core.MimeMessageSource;

public class MimeMessageJPASource implements MimeMessageSource {

    private final JPAMailRepository jpaMailRepository;
    private final String key;
    private final byte[] body;

    public MimeMessageJPASource(JPAMailRepository jpaMailRepository, String key, byte[] body) {
        this.jpaMailRepository = jpaMailRepository;
        this.key = key;
        this.body = body;
    }

    @Override
    public String getSourceId() {
        return jpaMailRepository.getRepositoryName() + "/" + key;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(body);
    }

    @Override
    public long getMessageSize() throws IOException {
        return body.length;
    }
}
