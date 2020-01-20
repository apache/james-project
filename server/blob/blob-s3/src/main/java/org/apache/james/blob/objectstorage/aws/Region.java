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

package org.apache.james.blob.objectstorage.aws;

import java.util.Objects;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public final class Region {

    private final String region;

    public static Region of(String region) {
        Preconditions.checkNotNull(region);
        
        return new Region(region);
    }

    private Region(String region) {
        this.region = region;
    }

    public software.amazon.awssdk.regions.Region asAws() {
        return software.amazon.awssdk.regions.Region.of(region);
    }


    @Override
    public boolean equals(Object o) {
        if (o instanceof Region) {
            Region that = (Region) o;
            return Objects.equals(this.region, that.region);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(region);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("region", region)
            .toString();
    }
}
