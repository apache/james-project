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

package org.apache.james.mailrepository.api;

import java.util.Objects;

import com.google.common.base.MoreObjects;

public class MailRepositoryProperties {

    static class Builder {

        @FunctionalInterface
        interface RequireBrowsable {
            ReadyToBuild browsable(boolean browsable);

            default ReadyToBuild canBrowse() {
                return browsable(true);
            }

            default ReadyToBuild canNotBrowse() {
                return browsable(false);
            }
        }

        static class ReadyToBuild {

            private final boolean browsable;

            ReadyToBuild(boolean browsable) {
                this.browsable = browsable;
            }

            MailRepositoryProperties build() {
                return new MailRepositoryProperties(browsable);
            }
        }
    }

    static Builder.RequireBrowsable builder() {
        return Builder.ReadyToBuild::new;
    }

    private final boolean browsable;

    private MailRepositoryProperties(boolean browsable) {
        this.browsable = browsable;
    }

    public boolean isBrowsable() {
        return browsable;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MailRepositoryProperties) {
            MailRepositoryProperties that = (MailRepositoryProperties) o;

            return Objects.equals(this.browsable, that.browsable);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(browsable);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("browsable", browsable)
            .toString();
    }
}
