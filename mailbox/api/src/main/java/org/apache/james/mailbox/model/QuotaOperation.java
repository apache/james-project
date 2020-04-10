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

package org.apache.james.mailbox.model;

import java.util.Objects;

import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;

import com.google.common.base.Preconditions;

public class QuotaOperation {
    private final QuotaRoot quotaRoot;
    private final QuotaCountUsage count;
    private final QuotaSizeUsage size;

    public QuotaOperation(QuotaRoot quotaRoot, QuotaCountUsage count, QuotaSizeUsage size) {
        Preconditions.checkArgument(count.asLong() >= 0, "Count should be positive");
        Preconditions.checkArgument(size.asLong() >= 0, "Size should be positive");

        this.quotaRoot = quotaRoot;
        this.count = count;
        this.size = size;
    }

    public QuotaRoot quotaRoot() {
        return quotaRoot;
    }

    public QuotaCountUsage count() {
        return count;
    }

    public QuotaSizeUsage size() {
        return size;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuotaOperation) {
            QuotaOperation quotaOperation = (QuotaOperation) o;

            return Objects.equals(this.quotaRoot, quotaOperation.quotaRoot)
                && Objects.equals(this.count, quotaOperation.count)
                && Objects.equals(this.size, quotaOperation.size);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(quotaRoot, count, size);
    }
}
