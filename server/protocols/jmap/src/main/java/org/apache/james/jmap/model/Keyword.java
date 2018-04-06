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

import java.util.Optional;

import javax.mail.Flags;

import org.apache.commons.lang.StringUtils;

import com.google.common.base.CharMatcher;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;

public class Keyword {
    private static final int FLAG_NAME_MIN_LENGTH = 1;
    private static final int FLAG_NAME_MAX_LENGTH = 255;
    private static final CharMatcher FLAG_NAME_PATTERN =
            CharMatcher.JAVA_LETTER_OR_DIGIT
                .or(CharMatcher.is('$'))
                .or(CharMatcher.is('_')
                .or(CharMatcher.is('-')));

    public static final Keyword DRAFT = new Keyword("$Draft");
    public static final Keyword SEEN = new Keyword("$Seen");
    public static final Keyword FLAGGED = new Keyword("$Flagged");
    public static final Keyword ANSWERED = new Keyword("$Answered");
    public static final Keyword DELETED = new Keyword("$Deleted");
    public static final Keyword RECENT = new Keyword("$Recent");
    public static final Keyword FORWARDED = new Keyword("$Forwarded");
    public static final Boolean FLAG_VALUE = true;

    private static final ImmutableList<Keyword> NON_EXPOSED_IMAP_KEYWORDS = ImmutableList.of(Keyword.RECENT, Keyword.DELETED);
    private static final ImmutableBiMap<Flags.Flag, Keyword> IMAP_SYSTEM_FLAGS = ImmutableBiMap.<Flags.Flag, Keyword>builder()
        .put(Flags.Flag.DRAFT, DRAFT)
        .put(Flags.Flag.SEEN, SEEN)
        .put(Flags.Flag.FLAGGED, FLAGGED)
        .put(Flags.Flag.ANSWERED, ANSWERED)
        .put(Flags.Flag.RECENT, RECENT)
        .put(Flags.Flag.DELETED, DELETED)
        .build();

    private final String flagName;

    public static Keyword fromFlag(Flags.Flag flag) {
        return IMAP_SYSTEM_FLAGS.get(flag);
    }

    public Keyword(String flagName) {
        Preconditions.checkArgument(isValid(flagName),
                "Flagname must not be null or empty, must have length form 1-255, " +
                    "must not contain charater with hex from '\\u0000' to '\\u00019' or {'(' ')' '{' ']' '%' '*' '\"' '\\'} ");
        this.flagName = flagName;
    }

    private boolean isValid(String flagName) {
        if (StringUtils.isBlank(flagName)) {
            return false;
        }
        if (flagName.length() < FLAG_NAME_MIN_LENGTH || flagName.length() > FLAG_NAME_MAX_LENGTH) {
            return false;
        }
        if (!FLAG_NAME_PATTERN.matchesAllOf(flagName)) {
            return false;
        }
        return true;
    }

    public String getFlagName() {
        return flagName;
    }

    public boolean isExposedImapKeyword() {
        return !NON_EXPOSED_IMAP_KEYWORDS.contains(this);
    }

    public boolean isDraft() {
        return DRAFT.equals(this);
    }

    public Optional<Flags.Flag> asSystemFlag() {
        return Optional.ofNullable(IMAP_SYSTEM_FLAGS.inverse()
            .get(this));
    }

    public Flags asFlags() {
        return asSystemFlag()
            .map(Flags::new)
            .orElse(new Flags(flagName));
    }

    @Override
    public final boolean equals(Object other) {
        if (other instanceof Keyword) {
            Keyword otherKeyword = (Keyword) other;
            return Objects.equal(flagName, otherKeyword.flagName);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(flagName);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("flagName", flagName)
            .toString();
    }

}
