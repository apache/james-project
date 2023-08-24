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

package org.apache.james.jmap.memory.upload;

import static org.apache.james.jmap.api.upload.UploadUsageRepositoryContract.USER_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.jmap.api.upload.UploadUsageRepository;
import org.apache.james.jmap.api.upload.UploadUsageRepositoryContract;
import org.junit.jupiter.api.BeforeEach;

import reactor.core.publisher.Mono;

public class InMemoryUploadUsageRepositoryTest implements UploadUsageRepositoryContract {

    private InMemoryUploadUsageRepository inMemoryUploadUsageRepository;

    @BeforeEach
    private void setup() {
        inMemoryUploadUsageRepository = new InMemoryUploadUsageRepository();
        resetCounterToZero();
    }

    private void resetCounterToZero() {
        Mono.from(inMemoryUploadUsageRepository.increaseSpace(USER_NAME(), QuotaSizeUsage.size(0))).block();
        QuotaSizeUsage quotaSizeUsage = Mono.from(inMemoryUploadUsageRepository.getSpaceUsage(USER_NAME())).block();
        Mono.from(inMemoryUploadUsageRepository.decreaseSpace(USER_NAME(), quotaSizeUsage)).block();
        QuotaSizeUsage actual = Mono.from(inMemoryUploadUsageRepository.getSpaceUsage(USER_NAME())).block();
        assertThat(actual.asLong()).isEqualTo(0l);
    }

    @Override
    public UploadUsageRepository uploadUsageRepository() {
        return inMemoryUploadUsageRepository;
    }

}
