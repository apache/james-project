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

import java.security.SecureRandom;
import java.util.function.Supplier;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class UidValidity {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long UPPER_EXCLUSIVE_BOUND = 1L << 32;

    /**
     * Despite RFC-3501 recommendations, we chose random as a UidVality generation mechanism.
     * Timestamp base approach can lead to UidValidity being the same for two mailboxes simultaneously generated, leading
     * to some IMPA clients not being able to detect changes.
     *
     * See https://issues.apache.org/jira/browse/JAMES-3074 for details
     */
    public static  UidValidity generate() {
        return fromSupplier(RANDOM::nextLong);
    }

    @VisibleForTesting
    static UidValidity fromSupplier(Supplier<Long> longSupplier) {
        long randomValue = Math.abs(longSupplier.get());
        long sanitizedRandomValue = 1 + (randomValue % (UPPER_EXCLUSIVE_BOUND - 1));
        return ofValid(sanitizedRandomValue);
    }

    /**
     * This method allows deserialization of potentially invalid already stored UidValidity, and should only be used for
     * compatibility purposes.
     *
     * Strongly favor uses of  {@link #ofValid(long)}
     */
    public static UidValidity of(long uidValidity) {
        return new UidValidity(uidValidity);
    }

    public static UidValidity ofValid(long uidValidity) {
        Preconditions.checkArgument(isValid(uidValidity), "uidValidity needs to be a non-zero unsigned 32-bit integer");
        return new UidValidity(uidValidity);
    }

    public static boolean isValid(long uidValidityAsLong) {
        return uidValidityAsLong > 0 && uidValidityAsLong < UPPER_EXCLUSIVE_BOUND;
    }

    private final long uidValidity;

    private UidValidity(long uidValidity) {
        this.uidValidity = uidValidity;
    }

    public boolean isValid() {
        // RFC-3501 section
        /*
        nz-number       = digit-nz *DIGIT
                    ; Non-zero unsigned 32-bit integer
                    ; (0 < n < 4,294,967,296)
         */
        return isValid(uidValidity);
    }


    public long asLong() {
        return uidValidity;
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof UidValidity) {
            UidValidity other = (UidValidity) obj;
            return Objects.equal(uidValidity, other.uidValidity);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(uidValidity);
    }

    @Override
    public String toString() {
        return MoreObjects
            .toStringHelper(this)
            .add("uidValidity", uidValidity)
            .toString();
    }
}
