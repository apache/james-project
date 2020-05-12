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

import java.util.Optional;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Strings;

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

    public interface Builder {
        @FunctionalInterface
        interface RequiresName {
            RequiresFullyQualifiedClassName name(String name);
        }

        @FunctionalInterface
        interface RequiresFullyQualifiedClassName {
            RequiresType fullyQualifiedClassName(String fullyQualifiedClassName);
        }

        @FunctionalInterface
        interface RequiresType {
            RequiresInfo type(Type type);
        }

        @FunctionalInterface
        interface RequiresInfo {
            default RequiresClassDocs info(String info) {
                if (Strings.isNullOrEmpty(info)) {
                    return noInfo();
                }

                return info(Optional.of(info));
            }

            default RequiresClassDocs noInfo() {
                return info(Optional.empty());
            }

            RequiresClassDocs info(Optional<String> info);
        }

        @FunctionalInterface
        interface RequiresClassDocs {
            default RequiresExperimental classDocs(String classDocs) {
                if (Strings.isNullOrEmpty(classDocs)) {
                    return noClassDocs();
                }

                return classDocs(Optional.of(classDocs));
            }

            default RequiresExperimental noClassDocs() {
                return classDocs(Optional.empty());
            }

            RequiresExperimental classDocs(Optional<String> classDocs);
        }

        @FunctionalInterface
        interface RequiresExperimental {
            default MailetMatcherDescriptor isExperimental() {
                return experimental(true);
            }

            default MailetMatcherDescriptor isNotExperimental() {
                return experimental(false);
            }

            MailetMatcherDescriptor experimental(boolean experimental);
        }

    }

    public static Builder.RequiresName builder() {
        return name -> fullyQualifiedClassName -> type -> info -> classDocs -> experimental ->
            new MailetMatcherDescriptor(name, fullyQualifiedClassName, type, info, classDocs, experimental);
    }

    private final String name;
    private final String fullyQualifiedClassName;
    private final Type type;
    private final Optional<String> info;
    private final Optional<String> classDocs;
    private final boolean experimental;

    private MailetMatcherDescriptor(String name, String fullyQualifiedClassName, Type type, Optional<String> info, Optional<String> classDocs, boolean experimental) {
        this.name = name;
        this.fullyQualifiedClassName = fullyQualifiedClassName;
        this.type = type;
        this.info = info;
        this.classDocs = classDocs;
        this.experimental = experimental;
    }

    public String getFullyQualifiedName() {
        return fullyQualifiedClassName;
    }

    public String getName() {
        return name;
    }

    public Optional<String> getInfo() {
        return info;
    }

    public Optional<String> getClassDocs() {
        return classDocs;
    }

    public Type getType() {
        return type;
    }

    public boolean isExperimental() {
        return experimental;
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
    public final int hashCode() {
        return Objects.hashCode(fullyQualifiedClassName, name, info, classDocs, type, experimental);
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof MailetMatcherDescriptor) {
            MailetMatcherDescriptor other = (MailetMatcherDescriptor) obj;
            return Objects.equal(this.fullyQualifiedClassName, other.fullyQualifiedClassName)
                && Objects.equal(this.name, other.name)
                && Objects.equal(this.classDocs, other.classDocs)
                && Objects.equal(this.info, other.info)
                && Objects.equal(this.type, other.type)
                && Objects.equal(this.experimental, other.experimental);
        }
        return false;
    }

}