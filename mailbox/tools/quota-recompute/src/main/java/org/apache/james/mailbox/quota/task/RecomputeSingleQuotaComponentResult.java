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

package org.apache.james.mailbox.quota.task;

import java.util.Objects;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

public class RecomputeSingleQuotaComponentResult {
    private final String quotaComponent;
    private final long processedIdentifierCount;
    private final ImmutableList<String> failedIdentifiers;

    public RecomputeSingleQuotaComponentResult(String quotaComponent, long processedIdentifierCount, ImmutableList<String> failedIdentifiers) {
        this.quotaComponent = quotaComponent;
        this.processedIdentifierCount = processedIdentifierCount;
        this.failedIdentifiers = failedIdentifiers;
    }

    public String getQuotaComponent() {
        return quotaComponent;
    }

    public long getProcessedIdentifierCount() {
        return processedIdentifierCount;
    }

    public ImmutableList<String> getFailedIdentifiers() {
        return failedIdentifiers;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof RecomputeSingleQuotaComponentResult) {
            RecomputeSingleQuotaComponentResult that = (RecomputeSingleQuotaComponentResult) o;

            return Objects.equals(this.quotaComponent, that.quotaComponent)
                && Objects.equals(this.processedIdentifierCount, that.processedIdentifierCount)
                && Objects.equals(this.failedIdentifiers, that.failedIdentifiers);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(quotaComponent, processedIdentifierCount, processedIdentifierCount);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("quotaComponent", quotaComponent)
            .add("processedIdentifierCount", processedIdentifierCount)
            .add("processedIdentifierCount", processedIdentifierCount)
            .toString();
    }
}
