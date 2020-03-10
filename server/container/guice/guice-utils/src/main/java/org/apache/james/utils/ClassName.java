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

package org.apache.james.utils;

import java.util.Objects;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;

public class ClassName {
    private final String name;

    public ClassName(String name) {
        Preconditions.checkNotNull(name);
        Preconditions.checkArgument(!name.isEmpty(), "A class name can not be empty");
        Preconditions.checkArgument(!name.startsWith(String.valueOf(PackageName.PART_SEPARATOR)), "A class name can not start with '.'");
        Preconditions.checkArgument(!name.endsWith(String.valueOf(PackageName.PART_SEPARATOR)), "A class name can not end with '.'");
        Splitter.on(PackageName.PART_SEPARATOR)
            .split(name)
            .forEach(part -> Preconditions.checkArgument(!part.isEmpty(), "Package part can not be empty within a class name"));

        this.name = name;
    }

    public String getName() {
        return name;
    }

    FullyQualifiedClassName appendPackage(PackageName aPackage) {
        return new FullyQualifiedClassName(aPackage.getName() + "." + name);
    }

    FullyQualifiedClassName asFullyQualified() {
        return new FullyQualifiedClassName(name);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ClassName) {
            ClassName className = (ClassName) o;

            return Objects.equals(this.name, className.name);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("name", name)
            .toString();
    }
}
