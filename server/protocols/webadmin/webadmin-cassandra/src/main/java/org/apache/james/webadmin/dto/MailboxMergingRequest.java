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

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class MailboxMergingRequest {
    private final String mergeOrigin;
    private final String mergeDestination;

    @JsonCreator
    public MailboxMergingRequest(@JsonProperty("mergeOrigin") String mergeOrigin,
                                 @JsonProperty("mergeDestination") String mergeDestination) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(mergeOrigin));
        Preconditions.checkArgument(!Strings.isNullOrEmpty(mergeDestination));
        this.mergeOrigin = mergeOrigin;
        this.mergeDestination = mergeDestination;
    }

    public String getMergeOrigin() {
        return mergeOrigin;
    }

    public String getMergeDestination() {
        return mergeDestination;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MailboxMergingRequest) {
            MailboxMergingRequest that = (MailboxMergingRequest) o;

            return Objects.equals(this.mergeOrigin, that.mergeOrigin)
                && Objects.equals(this.mergeDestination, that.mergeDestination);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(mergeOrigin, mergeDestination);
    }
}
