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

package org.apache.james.mailet;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

/**
 * Simple bean to describe a mailet or a matcher
 */
public class MailetMatcherDescriptor {

    /**
     * Enumerates subjects of description
     */
    public enum Type {

        MAILET("mailet"),
        MATCHER("matcher");

        private final String name;

        Type(String name) {
            this.name = name;
        }

        /**
         * Human readable name for type.
         *
         * @return not null
         */
        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return getName();
        }
    }

    private String fullyQualifiedClassName;

    private String name;

    private String info;

    private String classDocs;

    private Type type;

    private boolean experimental;

    public String getFullyQualifiedName() {
        return fullyQualifiedClassName;
    }

    public void setFullyQualifiedName(String fqName) {
        this.fullyQualifiedClassName = fqName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public String getClassDocs() {
        return classDocs;
    }

    public void setClassDocs(String classDocs) {
        this.classDocs = classDocs;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public boolean isExperimental() {
        return experimental;
    }

    public void setExperimental(boolean experimental) {
        this.experimental = experimental;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(MailetMatcherDescriptor.class)
                .add("fullyQualifiedClassName", fullyQualifiedClassName)
                .add("name", name)
                .add("info", info)
                .add("type", type)
                .add("experimental", experimental)
                .toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MailetMatcherDescriptor) {
            MailetMatcherDescriptor other = (MailetMatcherDescriptor) obj;
            return Objects.equal(this.fullyQualifiedClassName, other.fullyQualifiedClassName)
                && Objects.equal(this.name, other.name)
                && Objects.equal(this.info, other.info)
                && Objects.equal(this.type, other.type)
                && Objects.equal(this.experimental, other.experimental);
        }
        return false;
    }

}