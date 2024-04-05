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

package org.apache.james.jmap.model.mailbox;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.util.GuavaUtils;
import org.apache.james.util.OptionalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;

public class Rights {
    @VisibleForTesting
    static final Optional<Boolean> UNSUPPORTED = Optional.empty();

    public enum Right {
        Administer(MailboxACL.Right.Administer),
        Expunge(MailboxACL.Right.PerformExpunge),
        Insert(MailboxACL.Right.Insert),
        Lookup(MailboxACL.Right.Lookup),
        Read(MailboxACL.Right.Read),
        Seen(MailboxACL.Right.WriteSeenFlag),
        DeleteMessages(MailboxACL.Right.DeleteMessages),
        Write(MailboxACL.Right.Write);

        private final MailboxACL.Right right;

        Right(MailboxACL.Right right) {
            this.right = right;
        }

        @JsonValue
        public char asCharacter() {
            return right.asCharacter();
        }

        public MailboxACL.Right toMailboxRight() {
            return right;
        }

        public static Optional<Right> forRight(MailboxACL.Right right) {
            return OptionalUtils.executeIfEmpty(
                Arrays.stream(values())
                    .filter(jmapRight -> jmapRight.right == right)
                    .findAny(),
                () -> LOGGER.warn("Non handled right '{}'", right));
        }

        public static Right forChar(char c) {
            return Arrays.stream(values())
                .filter(right -> right.asCharacter() == c)
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("No matching right for '" + c + "'"));
        }
    }

    public static class Builder {
        private Multimap<Username, Right> rights;

        public Builder() {
            rights = ArrayListMultimap.create();
        }

        public Builder delegateTo(Username username, Right... rights) {
            delegateTo(username, Arrays.asList(rights));
            return this;
        }

        public Builder delegateTo(Username username, Collection<Right> rights) {
            this.rights.putAll(username, rights);
            return this;
        }

        public Builder combine(Builder builder) {
            this.rights.putAll(builder.rights);
            return this;
        }

        public Rights build() {
            return new Rights(rights);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Rights fromACL(MailboxACL acl) {
        return acl.getEntries()
            .entrySet()
            .stream()
            .filter(entry -> isSupported(entry.getKey()))
            .map(Rights::toRightsBuilder)
            .reduce(builder(), Builder::combine)
            .build();
    }

    private static Builder toRightsBuilder(Map.Entry<EntryKey, MailboxACL.Rfc4314Rights> entry) {
        return builder().delegateTo(
            Username.of(entry.getKey().getName()),
            fromACL(entry.getValue()));
    }

    private static List<Right> fromACL(MailboxACL.Rfc4314Rights rights) {
        return rights.list()
            .stream()
            .flatMap(right -> Right.forRight(right).stream())
            .collect(ImmutableList.toImmutableList());
    }

    private static boolean isSupported(EntryKey key) {
        if (key.isNegative()) {
            LOGGER.info("Negative keys are not supported");
            return false;
        }
        if (key.equals(MailboxACL.OWNER_KEY)) {
            return false;
        }
        if (key.getNameType() != MailboxACL.NameType.user) {
            LOGGER.info("{} is not supported. Only 'user' is.", key.getNameType());
            return false;
        }
        return true;
    }

    public static final Rights EMPTY = new Rights(ArrayListMultimap.create());

    private static final Logger LOGGER = LoggerFactory.getLogger(Rights.class);

    private final Multimap<Username, Right> rights;

    @JsonCreator
    public Rights(Map<Username, List<Right>> rights) {
        this(GuavaUtils.toMultimap(rights));
    }

    private Rights(Multimap<Username, Right> rights) {
        this.rights = rights;
    }

    @JsonAnyGetter
    public Map<Username, Collection<Right>> getRights() {
        return rights.asMap();
    }

    public Rights removeEntriesFor(Username username) {
        return new Rights(
            rights.asMap()
                .entrySet()
                .stream()
                .filter(entry -> !entry.getKey().equals(username))
                .flatMap(entry -> entry.getValue()
                    .stream()
                    .map(v -> Pair.of(entry.getKey(), v)))
                .collect(ImmutableListMultimap.toImmutableListMultimap(Pair::getKey, Pair::getValue)));
    }

    public MailboxACL toMailboxAcl() {
        BinaryOperator<MailboxACL> union = Throwing.binaryOperator(MailboxACL::union);

        return rights.asMap()
            .entrySet()
            .stream()
            .map(entry -> new MailboxACL(
                ImmutableMap.of(
                    EntryKey.createUserEntryKey(entry.getKey()),
                    toMailboxAclRights(entry.getValue()))))
            .reduce(MailboxACL.EMPTY, union);
    }

    public Optional<Boolean> mayReadItems(Username username) {
        return containsRight(username, Right.Read);
    }

    public Optional<Boolean> mayAddItems(Username username) {
        return containsRight(username, Right.Insert);
    }

    public Optional<Boolean> mayCreateChild(Username username) {
        return UNSUPPORTED;
    }

    public Optional<Boolean> mayRemoveItems(Username username) {
        return containsRight(username, Right.DeleteMessages);
    }

    public Optional<Boolean> mayRename(Username username) {
        return UNSUPPORTED;
    }

    public Optional<Boolean> mayDelete(Username username) {
        return UNSUPPORTED;
    }

    private Optional<Boolean> containsRight(Username username, Right right) {
        return Optional.ofNullable(rights.get(username))
            .filter(Predicate.not(Collection::isEmpty))
            .map(rightList -> rightList.contains(right));
    }

    private Rfc4314Rights toMailboxAclRights(Collection<Right> rights) {
        BinaryOperator<Rfc4314Rights> union = Throwing.binaryOperator(Rfc4314Rights::union);

        return rights.stream()
            .map(Right::toMailboxRight)
            .map(Throwing.function(Rfc4314Rights::new))
            .reduce(new Rfc4314Rights(), union);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Rights) {
            Rights that = (Rights) o;

            return Objects.equals(this.rights, that.rights);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(rights);
    }
}
