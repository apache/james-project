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

import java.util.Optional;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

public final class Project {
    public static Project of(ProjectName userName) {
        return new Project(userName, Optional.empty(), Optional.empty());
    }

    public static Project of(ProjectName userName, DomainName domainName) {
        return new Project(userName, Optional.of(domainName), Optional.empty());
    }

    public static Project of(ProjectName userName, DomainId domainId) {
        return new Project(userName, Optional.empty(), Optional.of(domainId));
    }

    private final ProjectName name;
    private final Optional<DomainName> domainName;
    private final Optional<DomainId> domainId;

    private Project(ProjectName name, Optional<DomainName> domainName, Optional<DomainId> domainId) {
        this.domainName = domainName;
        this.name = name;
        this.domainId = domainId;
    }

    public Optional<DomainName> domainName() {
        return domainName;
    }

    public Optional<DomainId> domainId() {
        return domainId;
    }

    public ProjectName name() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Project project = (Project) o;
        return Objects.equal(name, project.name) &&
            Objects.equal(domainName, project.domainName) &&
            Objects.equal(domainId, project.domainId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, domainName, domainId);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("domain", domainName)
            .add("name", name)
            .toString();
    }
}
