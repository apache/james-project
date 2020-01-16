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

package org.apache.james.blob.objectstorage.swift;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public final class Region {
    public static Region of(String value) {
        return new Region(value);
    }

    private final String region;

    private Region(String value) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(value),
            "%s cannot be null or empty", this.getClass().getSimpleName());
        this.region = value;
    }

    public String value() {
        return region;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Region) {
            Region that = (Region) o;
            return Objects.equal(region, that.region);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(region);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("region", region)
            .toString();
    }
}
