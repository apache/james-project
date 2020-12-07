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

package org.apache.mailet;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.apache.james.core.MailAddress;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

public class PerRecipientHeaders implements Serializable {

    private Multimap<MailAddress, Header> headersByRecipient;

    public PerRecipientHeaders() {
        headersByRecipient = ArrayListMultimap.create();
    }

    public Multimap<MailAddress, Header> getHeadersByRecipient() {
        return ArrayListMultimap.create(headersByRecipient);
    }

    public Collection<MailAddress> getRecipientsWithSpecificHeaders() {
        return headersByRecipient.keySet();
    }

    public Collection<Header> getHeadersForRecipient(MailAddress recipient) {
        return headersByRecipient.get(recipient);
    }

    public Collection<String> getHeaderNamesForRecipient(MailAddress recipient) {
        return headersByRecipient.get(recipient)
            .stream()
            .map(Header::getName)
            .collect(Guavate.toImmutableSet());
    }

    public PerRecipientHeaders addHeaderForRecipient(Header header, MailAddress recipient) {
        headersByRecipient.put(recipient, header);
        return this;
    }

    public PerRecipientHeaders addHeaderForRecipient(Header.Builder header, MailAddress recipient) {
        headersByRecipient.put(recipient, header.build());
        return this;
    }

    public void addAll(PerRecipientHeaders other) {
        headersByRecipient.putAll(other.headersByRecipient);
    }

    public static class Header implements Serializable {

        public static final String SEPARATOR = ": ";

        public static class Builder {
            private String name;
            private String value;
            
            public Builder name(String name) {
                this.name = name;
                return this;
            }
            
            public Builder value(String value) {
                this.value = value;
                return this;
            }
            
            public Header build() {
                Preconditions.checkNotNull(name);
                Preconditions.checkNotNull(value);
                return new Header(name, value);
            }
        }

        public static Builder builder() {
            return new Builder();
        }

        public static Header fromString(String value) {
            Preconditions.checkArgument(value.contains(SEPARATOR), "Header is string form needs to contain ': ' separator");

            List<String> parts = Splitter.on(SEPARATOR).splitToList(value);

            return new Header(
                parts.get(0),
                Joiner.on(SEPARATOR)
                    .join(parts.stream()
                        .skip(1)
                        .collect(Guavate.toImmutableList())));
        }

        private final String name;
        private final String value;

        @VisibleForTesting
        Header(String name, String value) {
            Preconditions.checkArgument(!name.contains(":"), "Header name should not contain separator");
            Preconditions.checkArgument(!name.contains("\n"), "Header name should not contain line break");

            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        public String asString() {
            return name + SEPARATOR + value;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Header) {
                Header that = (Header) o;

                return Objects.equal(this.name, that.name)
                    && Objects.equal(this.value, that.value);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hashCode(name, value);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("value", value)
                .toString();
        }
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof PerRecipientHeaders) {
            PerRecipientHeaders that = (PerRecipientHeaders) o;

            return Objects.equal(this.headersByRecipient, that.headersByRecipient);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(headersByRecipient);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("headersByRecipient", headersByRecipient)
            .toString();
    }
}
