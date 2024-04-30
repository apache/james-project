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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.mail.Flags;

import org.apache.james.core.Username;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Booleans;

/**
 * StorageDirective allows mailets to set storage instructions applied by mailet container.
 *
 * Usage:
 *
 * <pre>
 *     <code>
 *         StorageDirective.builder()
 *             .targetFolder("target")
 *             .seen(true)
 *             .important(true)
 *             .keywords(ImmutableList.of("abc", "def")
 *             .build()
 *             .encodeAsAttributes(Username.of("bob@localhost")
 *             .forEach(mail::setAttribute);
 *      </code></code>
 * </pre>
 *
 * This will result in this mail to be placed for bob@localhost into the folder target,
 * with user flags abc, def and marked as Flagged and Seen.
 */
public class StorageDirective {
    public static class Builder {
        private ImmutableList.Builder<String> targetFolders = ImmutableList.builder();
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
            value.ifPresent(targetFolders::add);
            return this;
        }

        public Builder targetFolder(String value) {
            targetFolders.add(value);
            return this;
        }

        public Builder targetFolders(Collection<String> values) {
            targetFolders.addAll(values);
            return this;
        }

        public Builder targetFolders(Optional<List<String>> values) {
            values.ifPresent(this::targetFolders);
            return this;
        }

        public Builder keywords(Optional<Collection<String>> value) {
            this.keywords = value;
            return this;
        }

        public StorageDirective build() {
            Preconditions.checkState(hasChanges(),
                "Expecting one of the storage directives to be specified: [targetFolder, seen, important, keywords]");

            Optional<Collection<String>> targetFolders = Optional.of(this.targetFolders.build()).filter(c -> !c.isEmpty()).map(c -> (Collection<String>) c);
            return new StorageDirective(targetFolders, seen, important, keywords);
        }

        private boolean hasChanges() {
            return Booleans.countTrue(
                seen.isPresent(),
                important.isPresent(),
                !targetFolders.build().isEmpty(),
                keywords.isPresent()) > 0;
        }

        public Optional<StorageDirective> buildOptional() {
            if (!hasChanges()) {
                return Optional.empty();
            }
            return Optional.of(build());
        }
    }

    private static final String DELIVERY_PATH_PREFIX = "DeliveryPath_";
    private static final String DELIVERY_PATHS_PREFIX = "DeliveryPaths_";
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

    private static Optional<Collection<String>> locateFolder(Username username, Mail mail) {
        AttributeName foldersAttribute = AttributeName.of(DELIVERY_PATHS_PREFIX + username.asString());
        if (mail.getAttribute(foldersAttribute).isPresent()) {
            return AttributeUtils.getValueAndCastFromMail(mail, foldersAttribute, Collection.class)
                .map(collection -> (Collection<AttributeValue>) collection)
                .map(collection -> collection.stream()
                    .map(AttributeValue::getValue)
                    .map(String.class::cast)
                    .collect(ImmutableList.toImmutableList()));
        }
        return AttributeUtils
            .getValueAndCastFromMail(mail, AttributeName.of(DELIVERY_PATH_PREFIX + username.asString()), String.class)
            .map(ImmutableList::of);
    }

    private final Optional<Collection<String>> targetFolder;
    private final Optional<Boolean> seen;
    private final Optional<Boolean> important;
    private final Optional<Collection<String>> keywords;

    private StorageDirective(Optional<Collection<String>> targetFolder,
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
            targetFolder.map(value -> new Attribute(AttributeName.of(DELIVERY_PATHS_PREFIX + username.asString()), asAttributeValue(value))),
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
            return new StorageDirective(Optional.of(ImmutableList.of(folder)), seen, important, keywords);
        }
        return this;
    }

    public Optional<Collection<String>> getTargetFolders() {
        return targetFolder;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof StorageDirective) {
            StorageDirective other = (StorageDirective) o;
            return Objects.equals(targetFolder, other.targetFolder)
                && Objects.equals(seen, other.seen)
                && Objects.equals(important, other.important)
                && Objects.equals(keywords, other.keywords);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(targetFolder, seen, important, keywords);
    }
}
