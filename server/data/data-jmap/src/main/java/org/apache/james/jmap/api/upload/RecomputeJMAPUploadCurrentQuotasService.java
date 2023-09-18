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
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.mailbox.quota.task.RecomputeSingleComponentCurrentQuotasService;

import reactor.core.publisher.Mono;

public class RecomputeJMAPUploadCurrentQuotasService implements RecomputeSingleComponentCurrentQuotasService {

    private final JMAPCurrentUploadUsageCalculator jmapCurrentUploadUsageCalculator;

    @Inject
    public RecomputeJMAPUploadCurrentQuotasService(JMAPCurrentUploadUsageCalculator jmapCurrentUploadUsageCalculator) {
        this.jmapCurrentUploadUsageCalculator = jmapCurrentUploadUsageCalculator;
    }

    @Override
    public QuotaComponent getQuotaComponent() {
        return QuotaComponent.JMAP_UPLOADS;
    }

    @Override
    public Mono<Void> recomputeCurrentQuotas(Username username) {
        return jmapCurrentUploadUsageCalculator.recomputeCurrentUploadUsage(username);
    }
}
