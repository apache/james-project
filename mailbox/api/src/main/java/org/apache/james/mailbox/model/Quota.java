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

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class Quota {

    public static final long UNLIMITED = -1;

    public static final long UNKNOWN = Long.MIN_VALUE;

    private static final Quota UNLIMITED_QUOTA = new Quota(UNKNOWN, UNLIMITED);

    public static Quota unlimited() {
        return UNLIMITED_QUOTA;
    }

    public static Quota quota(long used, long max) {
        return new Quota(used, max);
    }

    private final long max;
    private long used;

    private Quota(long used, long max) {
        this.used = used;
        this.max = max;
    }

    public long getMax() {
        return max;
    }

    public long getUsed() {
        return used;
    }

    public void addValueToQuota(long value) {
        used += value;
    }

    /**
     * Tells us if the quota is reached
     *
     * @return True if the user over uses the resource of this quota
     */
    public boolean isOverQuota() {
        return isOverQuotaWithAdditionalValue(0);
    }

    public boolean isOverQuotaWithAdditionalValue(long additionalValue) {
        Preconditions.checkArgument(additionalValue >= 0);
        return max != UNLIMITED
            && used + additionalValue > max;
    }

    @Override
    public String toString() {
        return used + "/" + max;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || ! (o instanceof  Quota)) {
            return false;
        }
        Quota other = (Quota) o;
        return used == other.getUsed()
            && max == other.getMax();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(used, max);
    }

}