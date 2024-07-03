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

package org.apache.james.smtpserver.priority;

import java.util.Objects;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class SmtpMtPriorityParameters {
    public static final String MT_PRIORITY_PARAMETER = "MT-PRIORITY";
    public static final int MIN_MT_PRIORITY_VALUE = -9;
    public static final int MAX_MT_PRIORITY_VALUE = 9;
    public static final int DEFAULT_MT_PRIORITY_VALUE = 0;

    public static class MtPriority {

        private final int priorityValue;

        public static Integer defaultPriorityValue() {
            return DEFAULT_MT_PRIORITY_VALUE;
        }

        public MtPriority(String value) {
            Preconditions.checkNotNull(value);
            int priority = Integer.parseInt(value);
            if (priority < MIN_MT_PRIORITY_VALUE || priority > MAX_MT_PRIORITY_VALUE) {
                throw new IllegalArgumentException("Invalid priority: According to RFC-6710 allowed values are from -9 to 9 inclusive");
            }
            this.priorityValue = priority;
        }

        public int getPriorityValue() {
            return priorityValue;
        }

        public String asString() {
            return String.valueOf(priorityValue);
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof MtPriority that) {
                return Objects.equals(this.priorityValue, that.priorityValue);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(priorityValue);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("priority value", priorityValue)
                .toString();
        }
    }
}