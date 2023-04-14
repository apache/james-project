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

package org.apache.james.smtpserver.futurerelease;

import java.time.Duration;
import java.util.Objects;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class FutureReleaseParameters {
    public static final String HOLDFOR_PARAMETER = "HOLDFOR";
    public static final String HOLDUNTIL_PARAMETER = "HOLDUNTIL";
    public static final Duration MAX_HOLD_FOR_SUPPORTED = Duration.ofDays(1);

    public static class HoldFor {
        public static HoldFor of(Duration value) {
            Preconditions.checkNotNull(value);
            return new HoldFor(value);
        }

        private final Duration value;

        private HoldFor(Duration value) {
            this.value = value;
        }

        public Duration value() {
            return value;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof HoldFor) {
                HoldFor holdFor = (HoldFor) o;
                return Objects.equals(this.value, holdFor.value);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("value", value)
                .toString();
        }
    }
}
