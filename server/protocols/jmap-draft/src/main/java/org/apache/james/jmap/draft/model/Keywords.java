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

package org.apache.james.jmap.draft.model;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jakarta.mail.Flags;

import org.apache.james.mailbox.FlagsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class Keywords {

    public static final Keywords DEFAULT_VALUE = new Keywords(ImmutableSet.of());
    private static final Logger LOGGER = LoggerFactory.getLogger(Keywords.class);
    private static final KeywordsFactory STRICT_KEYWORDS_FACTORY = new KeywordsFactory(
        KeywordsFactory.KeywordsValidator.THROW_ON_IMAP_NON_EXPOSED_KEYWORDS,
        KeywordsFactory.KeywordFilter.KEEP_ALL,
        KeywordsFactory.ToKeyword.STRICT);
    private static final KeywordsFactory LENIENT_KEYWORDS_FACTORY = new KeywordsFactory(
        KeywordsFactory.KeywordsValidator.IGNORE_NON_EXPOSED_IMAP_KEYWORDS,
        KeywordsFactory.KeywordFilter.FILTER_IMAP_NON_EXPOSED_KEYWORDS,
        KeywordsFactory.ToKeyword.LENIENT);

    private FlagsBuilder combiner(FlagsBuilder firstBuilder, FlagsBuilder secondBuilder) {
        return firstBuilder.add(secondBuilder.build());
    }

    private FlagsBuilder accumulator(FlagsBuilder accumulator, Keyword keyword) {
        return accumulator.add(keyword.asFlags());
    }

    public static class KeywordsFactory {
        @FunctionalInterface
        interface KeywordsValidator {
            KeywordsValidator THROW_ON_IMAP_NON_EXPOSED_KEYWORDS = keyword -> Preconditions.checkArgument(
                keyword.isExposedImapKeyword(),
                "Does not allow to update 'Deleted' or 'Recent' flag");

            KeywordsValidator IGNORE_NON_EXPOSED_IMAP_KEYWORDS = keyword -> { };

            void validate(Keyword keywords);
        }

        @FunctionalInterface
        interface KeywordFilter extends Predicate<Keyword> {
            KeywordFilter FILTER_IMAP_NON_EXPOSED_KEYWORDS = Keyword::isExposedImapKeyword;
            KeywordFilter KEEP_ALL = keyword -> true;
        }

        @FunctionalInterface
        interface ToKeyword {
            ToKeyword STRICT = value -> Optional.of(Keyword.of(value));
            ToKeyword LENIENT = value -> {
                Optional<Keyword> result = Keyword.parse(value);
                if (!result.isPresent()) {
                    LOGGER.warn("Fail to parse {} flag", value);
                }
                return result;
            };

            Optional<Keyword> toKeyword(String value);

            default Stream<Keyword> asKeywordStream(String value) {
                return toKeyword(value).stream();
            }
        }

        private final KeywordsValidator validator;
        private final Predicate<Keyword> filter;
        private final ToKeyword toKeyword;

        public KeywordsFactory(KeywordsValidator validator, Predicate<Keyword> filter, ToKeyword toKeyword) {
            this.validator = validator;
            this.filter = filter;
            this.toKeyword = toKeyword;
        }

        public Keywords fromSet(Set<Keyword> setKeywords) {
            return fromStream(setKeywords.stream());
        }

        public Keywords fromStream(Stream<Keyword> keywordStream) {
            return new Keywords(keywordStream
                    .peek(validator::validate)
                    .filter(filter)
                    .collect(ImmutableSet.toImmutableSet()));
        }

        public Keywords from(Keyword... keywords) {
            return fromStream(Arrays.stream(keywords));
        }

        public Keywords fromCollection(Collection<String> keywords) {
            return fromStream(keywords.stream()
                    .flatMap(toKeyword::asKeywordStream));
        }

        @VisibleForTesting
        Keywords fromMap(Map<String, Boolean> mapKeywords) {
            Preconditions.checkArgument(mapKeywords.values()
                .stream()
                .allMatch(keywordValue -> keywordValue), "Keyword must be true");

            return fromCollection(mapKeywords.keySet());
        }

        public Keywords fromFlags(Flags flags) {
            return fromStream(Stream.concat(
                        Stream.of(flags.getUserFlags())
                            .flatMap(toKeyword::asKeywordStream),
                        Stream.of(flags.getSystemFlags())
                            .map(Keyword::fromFlag)));
        }
    }

    /**
     * This Keywords factory will filter out invalid keywords (containing inavlid character, being too long or being non
     * exposed IMAP flags)
     */
    public static KeywordsFactory strictFactory() {
        return STRICT_KEYWORDS_FACTORY;
    }

    /**
     * This Keywords factory will throw upon invalid keywords (containing inavlid character, being too long or being non
     * exposed IMAP flags)
     *
     * @throws IllegalArgumentException
     */
    public static KeywordsFactory lenientFactory() {
        return LENIENT_KEYWORDS_FACTORY;
    }

    private final ImmutableSet<Keyword> keywords;

    private Keywords(ImmutableSet<Keyword> keywords) {
        this.keywords = keywords;
    }

    public Flags asFlags() {
        return keywords.stream()
            .reduce(FlagsBuilder.builder(), this::accumulator, this::combiner)
            .build();
    }

    public Flags asFlagsWithRecentAndDeletedFrom(Flags originFlags) {
        Flags flags = asFlags();
        if (originFlags.contains(Flags.Flag.DELETED)) {
            flags.add(Flags.Flag.DELETED);
        }
        if (originFlags.contains(Flags.Flag.RECENT)) {
            flags.add(Flags.Flag.RECENT);
        }

        return flags;
    }

    public ImmutableMap<String, Boolean> asMap() {
        return keywords.stream()
            .collect(ImmutableMap.toImmutableMap(Keyword::getFlagName, keyword -> Keyword.FLAG_VALUE));
    }

    public ImmutableSet<Keyword> getKeywords() {
        return keywords;
    }

    public boolean contains(Keyword keyword) {
        return keywords.contains(keyword);
    }

    @Override
    public final boolean equals(Object other) {
        if (other instanceof Keywords) {
            Keywords otherKeyword = (Keywords) other;
            return Objects.equal(keywords, otherKeyword.keywords);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(keywords);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("keywords", keywords)
            .toString();
    }

}
