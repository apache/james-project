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

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.jmap.api.model.UploadMetaData;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class JMAPCurrentUploadUsageCalculator {

    private final UploadRepository uploadRepository;
    private final UploadUsageRepository uploadUsageRepository;

    @Inject
    public JMAPCurrentUploadUsageCalculator(UploadRepository uploadRepository, UploadUsageRepository uploadUsageRepository) {
        this.uploadRepository = uploadRepository;
        this.uploadUsageRepository = uploadUsageRepository;
    }

    public Mono<Void> recomputeCurrentUploadUsage(Username username) {
        return Flux.from(uploadRepository.listUploads(username))
            .map(UploadMetaData::sizeAsLong)
            .reduce(Long::sum)
            .flatMap(sum -> Mono.from(uploadUsageRepository.resetSpace(username, QuotaSizeUsage.size(sum))));
    }
}
