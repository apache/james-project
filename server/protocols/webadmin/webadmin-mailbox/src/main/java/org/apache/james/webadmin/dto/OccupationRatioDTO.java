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

package org.apache.james.webadmin.dto;

import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRatio;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaSize;

public class OccupationRatioDTO {

    public static OccupationRatioDTO from(Quota<QuotaSize> sizeQuota, Quota<QuotaCount> countQuota) {
        return new OccupationRatioDTO(
            sizeQuota.getRatio(),
            countQuota.getRatio(),
            QuotaRatio.from(sizeQuota, countQuota).max());
    }

    private final double size;
    private final double count;
    private final double max;

    private OccupationRatioDTO(double size, double count, double max) {
        this.size = size;
        this.count = count;
        this.max = max;
    }

    public double getSize() {
        return size;
    }

    public double getCount() {
        return count;
    }

    public double getMax() {
        return max;
    }
}
