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

package org.apache.james.mailbox.quota;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.model.Quota;

public interface QuotaFixture {
    interface Counts {
        Quota<QuotaCountLimit, QuotaCountUsage> _32_PERCENT = Quota.<QuotaCountLimit, QuotaCountUsage>builder()
            .used(QuotaCountUsage.count(32))
            .computedLimit(QuotaCountLimit.count(100))
            .build();

        Quota<QuotaCountLimit, QuotaCountUsage> _40_PERCENT = Quota.<QuotaCountLimit, QuotaCountUsage>builder()
            .used(QuotaCountUsage.count(40))
            .computedLimit(QuotaCountLimit.count(100))
            .build();

        Quota<QuotaCountLimit, QuotaCountUsage> _52_PERCENT = Quota.<QuotaCountLimit, QuotaCountUsage>builder()
            .used(QuotaCountUsage.count(52))
            .computedLimit(QuotaCountLimit.count(100))
            .build();

        Quota<QuotaCountLimit, QuotaCountUsage> _72_PERCENT = Quota.<QuotaCountLimit, QuotaCountUsage>builder()
            .used(QuotaCountUsage.count(72))
            .computedLimit(QuotaCountLimit.count(100))
            .build();

        Quota<QuotaCountLimit, QuotaCountUsage> _82_PERCENT = Quota.<QuotaCountLimit, QuotaCountUsage>builder()
            .used(QuotaCountUsage.count(82))
            .computedLimit(QuotaCountLimit.count(100))
            .build();

        Quota<QuotaCountLimit, QuotaCountUsage> _85_PERCENT = Quota.<QuotaCountLimit, QuotaCountUsage>builder()
            .used(QuotaCountUsage.count(85))
            .computedLimit(QuotaCountLimit.count(100))
            .build();

        Quota<QuotaCountLimit, QuotaCountUsage> _92_PERCENT = Quota.<QuotaCountLimit, QuotaCountUsage>builder()
            .used(QuotaCountUsage.count(92))
            .computedLimit(QuotaCountLimit.count(100))
            .build();

        Quota<QuotaCountLimit, QuotaCountUsage> _UNLIMITED = Quota.<QuotaCountLimit, QuotaCountUsage>builder()
            .used(QuotaCountUsage.count(92))
            .computedLimit(QuotaCountLimit.unlimited())
            .build();
    }

    interface Sizes {
        Quota<QuotaSizeLimit, QuotaSizeUsage> _30_PERCENT = Quota.<QuotaSizeLimit, QuotaSizeUsage>builder()
            .used(QuotaSizeUsage.size(30))
            .computedLimit(QuotaSizeLimit.size(100))
            .build();
        Quota<QuotaSizeLimit, QuotaSizeUsage> _42_PERCENT = Quota.<QuotaSizeLimit, QuotaSizeUsage>builder()
            .used(QuotaSizeUsage.size(42))
            .computedLimit(QuotaSizeLimit.size(100))
            .build();

        Quota<QuotaSizeLimit, QuotaSizeUsage> _55_PERCENT = Quota.<QuotaSizeLimit, QuotaSizeUsage>builder()
            .used(QuotaSizeUsage.size(55))
            .computedLimit(QuotaSizeLimit.size(100))
            .build();

        Quota<QuotaSizeLimit, QuotaSizeUsage> _60_PERCENT = Quota.<QuotaSizeLimit, QuotaSizeUsage>builder()
            .used(QuotaSizeUsage.size(60))
            .computedLimit(QuotaSizeLimit.size(100))
            .build();

        Quota<QuotaSizeLimit, QuotaSizeUsage> _75_PERCENT = Quota.<QuotaSizeLimit, QuotaSizeUsage>builder()
            .used(QuotaSizeUsage.size(75))
            .computedLimit(QuotaSizeLimit.size(100))
            .build();

        Quota<QuotaSizeLimit, QuotaSizeUsage> _82_PERCENT = Quota.<QuotaSizeLimit, QuotaSizeUsage>builder()
            .used(QuotaSizeUsage.size(82))
            .computedLimit(QuotaSizeLimit.size(100))
            .build();

        Quota<QuotaSizeLimit, QuotaSizeUsage> _92_PERCENT = Quota.<QuotaSizeLimit, QuotaSizeUsage>builder()
            .used(QuotaSizeUsage.size(92))
            .computedLimit(QuotaSizeLimit.size(100))
            .build();

        Quota<QuotaSizeLimit, QuotaSizeUsage> _992_PERTHOUSAND = Quota.<QuotaSizeLimit, QuotaSizeUsage>builder()
            .used(QuotaSizeUsage.size(992))
            .computedLimit(QuotaSizeLimit.size(1000))
            .build();
    }

}
