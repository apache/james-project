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

package org.apache.james.mailrepository.cassandra;

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.blob.api.BlobId;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.mailet.Mail;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MergingCassandraMailRepositoryMailDao implements CassandraMailRepositoryMailDaoAPI {

    private final CassandraMailRepositoryMailDAO v1;
    private final CassandraMailRepositoryMailDaoV2 v2;

    @Inject
    @VisibleForTesting
    MergingCassandraMailRepositoryMailDao(CassandraMailRepositoryMailDAO v1, CassandraMailRepositoryMailDaoV2 v2) {
        this.v1 = v1;
        this.v2 = v2;
    }

    @Override
    public Mono<Void> store(MailRepositoryUrl url, Mail mail, BlobId headerId, BlobId bodyId) {
        return v2.store(url, mail, headerId, bodyId);
    }

    @Override
    public Mono<Void> remove(MailRepositoryUrl url, MailKey key) {
        return Flux.merge(v1.remove(url, key), v2.remove(url, key)).then();
    }

    @Override
    public Mono<Optional<MailDTO>> read(MailRepositoryUrl url, MailKey key) {
        return v2.read(url, key)
            .filter(Optional::isPresent)
            .switchIfEmpty(v1.read(url, key));
    }
}
