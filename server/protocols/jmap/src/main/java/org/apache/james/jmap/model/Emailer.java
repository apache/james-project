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

package org.apache.james.jmap.model;

import java.util.Objects;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

@JsonDeserialize(builder = Emailer.Builder.class)
public class Emailer {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private String name;
        private String email;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Emailer build() {
            Preconditions.checkState(!Strings.isNullOrEmpty(name), "'name' is mandatory");
            Preconditions.checkState(!Strings.isNullOrEmpty(email), "'email' is mandatory");
            Preconditions.checkState(email.contains("@"), "'email' must contain '@' character");
            return new Emailer(name, email);
        }
    }

    private final String name;
    private final String email;

    @VisibleForTesting Emailer(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Emailer) {
            Emailer otherEMailer = (Emailer) o;
            return Objects.equals(name, otherEMailer.name)
                && Objects.equals(email, otherEMailer.email);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, email);
    }

    @Override
    public String toString() {
        return com.google.common.base.Objects.toStringHelper(this)
            .add("name", name)
            .add("email", email)
            .toString();
    }
}
