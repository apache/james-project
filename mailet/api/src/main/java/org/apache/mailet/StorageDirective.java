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

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

import javax.mail.Flags;

import org.apache.james.core.Username;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Booleans;

public class StorageDirective {
    public static class Builder {
        private Optional<String> targetFolder = Optional.empty();
        private Optional<Boolean> seen = Optional.empty();
        private Optional<Boolean> important = Optional.empty();
        private Optional<Collection<String>> keywords = Optional.empty();

        public Builder seen(Optional<Boolean> value) {
            this.seen = value;
            return this;
        }

        public Builder important(Optional<Boolean> value) {
            this.important = value;
            return this;
        }

        public Builder targetFolder(Optional<String> value) {
            this.targetFolder = value;
            return this;
        }

        public Builder targetFolder(String value) {
            this.targetFolder = Optional.of(value);
            return this;
        }

        public Builder keywords(Optional<Collection<String>> value) {
            this.keywords = value;
            return this;
        }

        public StorageDirective build() {
            Preconditions.checkState(
                Booleans.countTrue(
                    seen.isPresent(),
                    important.isPresent(),
                    targetFolder.isPresent(),
                    keywords.isPresent()) > 0,
                "Expecting one of the storage directives to be specified: [targetFolder, seen, important, keywords]");

            return new StorageDirective(targetFolder, seen, important, keywords);
        }
    }

    private static final String DELIVERY_PATH_PREFIX = "DeliveryPath_";
    private static final String SEEN_PREFIX = "Seen_";
    private static final String IMPORTANT_PREFIX = "Important_";
    private static final String KEYWORDS_PREFIX = "Keywords_";

    public static Builder builder() {
        return new Builder();
    }

    public static StorageDirective fromMail(Username username, Mail mail) {
        Optional<Attribute> seen = mail.getAttribute(AttributeName.of(SEEN_PREFIX + username.asString()));
        Optional<Attribute> important = mail.getAttribute(AttributeName.of(IMPORTANT_PREFIX + username.asString()));
        Optional<Attribute> keywords = mail.getAttribute(AttributeName.of(KEYWORDS_PREFIX + username.asString()));

        return new StorageDirective(
            locateFolder(username, mail),
            asBooleanOptional(seen),
            asBooleanOptional(important),
            extractKeywords(keywords));
    }

    @SuppressWarnings("unchecked")
    private static Optional<Collection<String>> extractKeywords(Optional<Attribute> keywords) {
        Stream<String> stream = keywords
            .map(Attribute::getValue)
            .map(AttributeValue::getValue)
            .filter(Collection.class::isInstance)
            .map(Collection.class::cast)
            .stream()
            .flatMap(Collection::stream)
            .filter(AttributeValue.class::isInstance)
            .map(AttributeValue.class::cast)
            .map(a -> ((AttributeValue<?>) a).getValue())
            .filter(String.class::isInstance)
            .map(String.class::cast);
        Collection<String> result = stream.collect(ImmutableSet.toImmutableSet());

        return Optional.of(result)
            .filter(c -> !c.isEmpty());
    }

    private static Optional<Boolean> asBooleanOptional(Optional<Attribute> attr) {
        return attr.map(Attribute::getValue)
            .map(AttributeValue::getValue)
            .filter(Boolean.class::isInstance)
            .map(Boolean.class::cast);
    }

    private static Optional<String> locateFolder(Username username, Mail mail) {
        return AttributeUtils
            .getValueAndCastFromMail(mail, AttributeName.of(DELIVERY_PATH_PREFIX + username.asString()), String.class);
    }

    private final Optional<String> targetFolder;
    private final Optional<Boolean> seen;
    private final Optional<Boolean> important;
    private final Optional<Collection<String>> keywords;

    private StorageDirective(Optional<String> targetFolder,
                            Optional<Boolean> seen,
                            Optional<Boolean> important,
                            Optional<Collection<String>> keywords) {
        this.targetFolder = targetFolder;
        this.seen = seen;
        this.important = important;
        this.keywords = keywords;
    }

    public Optional<Flags> getFlags() {
        if (seen.isEmpty() && important.isEmpty() && keywords.isEmpty()) {
            return Optional.empty();
        }

        Flags flags = new Flags();
        seen.ifPresent(seenFlag -> {
                if (seenFlag) {
                    flags.add(Flags.Flag.SEEN);
                }
            });
        important.ifPresent(seenFlag -> {
                if (seenFlag) {
                    flags.add(Flags.Flag.FLAGGED);
                }
            });
        keywords.stream().flatMap(Collection::stream).forEach(flags::add);
        return Optional.of(flags);
    }

    public Stream<Attribute> encodeAsAttributes(Username username) {
        return Stream.of(
            targetFolder.map(value -> new Attribute(AttributeName.of(DELIVERY_PATH_PREFIX + username.asString()), AttributeValue.of(value))),
            seen.map(value -> new Attribute(AttributeName.of(SEEN_PREFIX + username.asString()), AttributeValue.of(value))),
            important.map(value -> new Attribute(AttributeName.of(IMPORTANT_PREFIX + username.asString()), AttributeValue.of(value))),
            keywords.map(value -> new Attribute(AttributeName.of(KEYWORDS_PREFIX + username.asString()), asAttributeValue(value))))
            .flatMap(Optional::stream);
    }

    private AttributeValue asAttributeValue(Collection<String> value) {
        return AttributeValue.of(value.stream()
            .map(AttributeValue::of)
            .collect(ImmutableSet.toImmutableSet()));
    }

    public StorageDirective withDefaultFolder(String folder) {
        if (targetFolder.isEmpty()) {
            return new StorageDirective(Optional.of(folder), seen, important, keywords);
        }
        return this;
    }

    public Optional<String> getTargetFolder() {
        return targetFolder;
    }
}
