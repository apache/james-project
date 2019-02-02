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

import org.apache.james.core.quota.QuotaCount;
import org.apache.james.core.quota.QuotaSize;
import org.apache.james.mailbox.model.Quota;

public class OccupationDTO {
    public static OccupationDTO from(Quota<QuotaSize> sizeQuota, Quota<QuotaCount> countQuota) {
        return new OccupationDTO(
            sizeQuota.getUsed().asLong(),
            countQuota.getUsed().asLong(),
            OccupationRatioDTO.from(sizeQuota, countQuota));
    }

    private final long size;
    private final long count;
    private final OccupationRatioDTO ratio;

    private OccupationDTO(long size, long count, OccupationRatioDTO ratio) {
        this.size = size;
        this.count = count;
        this.ratio = ratio;
    }

    public long getSize() {
        return size;
    }

    public long getCount() {
        return count;
    }

    public OccupationRatioDTO getRatio() {
        return ratio;
    }
}
