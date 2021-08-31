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

package org.apache.james.user.lib;

import java.util.Objects;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class LocalPart {
    public static LocalPart of(String value) {
        Preconditions.checkArgument(value != null, "LocalPart should not be null or empty");
        Preconditions.checkArgument(!value.trim().isEmpty(), "LocalPart should not be null or empty after being trimmed");
        Preconditions.checkArgument(!value.contains("@"), "LocalPart should not contain '@'");

        return new LocalPart(value);
    }

    private final String value;

    public LocalPart(String value) {
        this.value = value;
    }

    public String asString() {
        return value;
    }

    public Username withDomain(Domain domain) {
        return Username.fromLocalPartWithDomain(asString(), domain);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof LocalPart) {
            LocalPart localPart = (LocalPart) o;

            return Objects.equals(this.value, localPart.value);
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
