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

package org.apache.james.mailbox.model;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.mailbox.exception.UnsupportedRightException;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Stores an Access Control List (ACL) applicable to a mailbox. Inspired by
 * RFC4314 IMAP4 Access Control List (ACL) Extension.
 *
 * Implementations must be immutable. Implementations should override
 * {@link #hashCode()} and {@link #equals(Object)}.
 *
 */
public class MailboxACL {

    private static EnumSet<Right> copyOf(Collection<Right> collection) {
        if (collection.isEmpty()) {
            return EnumSet.noneOf(Right.class);
        }
        return EnumSet.copyOf(collection);
    }

    /**
     * SETACL command mode.
     */
    public enum EditMode {
        ADD, REMOVE, REPLACE
    }

    public enum NameType {
        group, special, user
    }

    /**
     * Special name literals.
     */
    public enum SpecialName {
        anybody, authenticated, owner
    }

    /**
     * SETACL third argument prefix
     */
    public static final char ADD_RIGHTS_MARKER = '+';

    /**
     * Marks groups when (de)serializing {@link MailboxACLEntryKey}s.
     *
     * @see MailboxACLEntryKey#serialize()
     */
    public static final char DEFAULT_GROUP_MARKER = '$';

    /**
     * Marks negative when (de)serializing {@link MailboxACLEntryKey}s.
     *
     * @see MailboxACLEntryKey#serialize()
     */
    public static final char DEFAULT_NEGATIVE_MARKER = '-';

    /**
     * SETACL third argument prefix
     */
    public static final char REMOVE_RIGHTS_MARKER = '-';

    /**
     * Single right applicable to a mailbox.
     */
    public enum Right {
        Administer('a'), // (perform SETACL/DELETEACL/GETACL/LISTRIGHTS)
        PerformExpunge('e'), //perform EXPUNGE and expunge as a part of CLOSE
        Insert('i'), //insert (perform APPEND, COPY into mailbox)
        /*
        * create mailboxes (CREATE new sub-mailboxes in any
        * implementation-defined hierarchy, parent mailbox for the new mailbox
        * name in RENAME)
        * */
        CreateMailbox('k'),
        Lookup('l'), //lookup (mailbox is visible to LIST/LSUB commands, SUBSCRIBE mailbox)
        Post('p'), //post (send mail to submission address for mailbox, not enforced by IMAP4 itself)
        Read('r'), //read (SELECT the mailbox, perform STATUS)
        /**
         * keep seen/unseen information across sessions (set or clear \SEEN
         * flag via STORE, also set \SEEN during APPEND/COPY/ FETCH BODY[...])
         */
        WriteSeenFlag('s'),
        DeleteMessages('t'), //delete messages (set or clear \DELETED flag via STORE, set \DELETED flag during APPEND/COPY)
        /**
         * write (set or clear flags other than \SEEN and \DELETED via
         * STORE, also set them during APPEND/COPY)
         */
        Write('w'),
        DeleteMailbox('x'); //delete mailbox (DELETE mailbox, old mailbox name in RENAME)

        private final char rightCharacter;

        Right(char rightCharacter) {
            this.rightCharacter = rightCharacter;
        }

        /**
         * Returns the char representation of this right.
         *
         * @return char representation of this right
         */
        public char asCharacter() {
            return rightCharacter;
        }

        public static final EnumSet<Right> allRights = EnumSet.allOf(Right.class);

        public static Right forChar(char c) throws UnsupportedRightException {
            return Right.allRights
                .stream()
                .filter(r -> r.asCharacter() == c)
                .findFirst()
                .orElseThrow(() -> new UnsupportedRightException(c));
        }
    }

    /**
     * Holds the collection of {@link MailboxACL.Right}s.
     *
     * Implementations may decide to support only a specific range of rights,
     * e.g. the Standard Rights of RFC 4314 section 2.1.
     *
     * Implementations must not allow adding or removing of elements once this
     * MailboxACLRights are initialized.
     */
    public static class Rfc4314Rights {
        private static final char c_ObsoleteCreate = 'c';
        private static final char d_ObsoleteDelete = 'd';

        private final EnumSet<Right> value;

        private Rfc4314Rights(EnumSet<Right> rights) {
            this.value = copyOf(rights);
        }

        private Rfc4314Rights() {
            this(EnumSet.noneOf(Right.class));
        }

        public Rfc4314Rights(Right... rights) {
            this(copyOf(Arrays.asList(rights)));
        }

        public Rfc4314Rights(Right right) throws UnsupportedRightException {
            this.value = EnumSet.of(Right.forChar(right.asCharacter()));
        }

        public Rfc4314Rights(String serializedRfc4314Rights) throws UnsupportedRightException {
            this.value = copyOf(serializedRfc4314Rights.chars()
                .mapToObj(i -> (char) i)
                .flatMap(Throwing.function(this::convert).sneakyThrow())
                .collect(Collectors.toList()));
        }

        private Stream<Right> convert(char flag) throws UnsupportedRightException {
            switch (flag) {
            case c_ObsoleteCreate:
                return Stream.of(Right.CreateMailbox, Right.DeleteMailbox);
            case d_ObsoleteDelete:
                return Stream.of(Right.PerformExpunge, Right.DeleteMessages, Right.DeleteMailbox);
            default:
                return Stream.of(Right.forChar(flag));
            }
        }

        /**
         * Tells whether this contains the given right.
         *
         * @throws UnsupportedRightException if the given right is not supported.
         */
        public boolean contains(char flag) throws UnsupportedRightException {
            return contains(Right.forChar(flag));
        }

        public boolean contains(Right right) throws UnsupportedRightException {
            return value.contains(Right.forChar(right.asCharacter()));
        }

        public boolean equals(Object o) {
            if (o instanceof Rfc4314Rights) {
                Rfc4314Rights that = (Rfc4314Rights) o;
                return this.value.equals(that.value);
            }
            return false;
        }

        /**
         * Performs the set theoretic operation of relative complement of
         * toRemove MailboxACLRights in this MailboxACLRights.
         *
         * A schematic example: "lrw".except("w") returns "lr".
         *
         * Implementations must return a new unmodifiable instance of
         * {@link MailboxACL.MailboxACLRights}. However, implementations may decide to
         * return this or toRemove parameter value in case the result would be
         * equal to the respective one of those.
         *
         * @throws UnsupportedRightException
         */
        public Rfc4314Rights except(Rfc4314Rights toRemove) throws UnsupportedRightException {
            EnumSet<Right> copy = copyOf(value);
            Optional.ofNullable(toRemove)
                .ifPresent(rights -> copy.removeAll(rights.value));
            return new Rfc4314Rights(copy);
        }

        /**
         * Tells if this set of rights is empty.
         *
         * @return true if there are no rights in this set; false otherwise.
         */
        public boolean isEmpty() {
            return value.isEmpty();
        }

        /**
         * Tells whether the implementation supports the given right.
         *
         * @param right
         * @return true if this supports the given right.
         */
        public boolean isSupported(Right right) {
            try {
                contains(right.asCharacter());
                return true;
            } catch (UnsupportedRightException e) {
                return false;
            }
        }

        public Iterator<Right> iterator() {
            ImmutableList<Right> rights = ImmutableList.copyOf(value);
            return rights.iterator();
        }

        public List<Right> list() {
            return ImmutableList.copyOf(value);
        }

        /**
         * Returns a serialized form of this {@link MailboxACL.Right} as
         * {@link String}.
         *
         * @return a {@link String}
         */
        public String serialize() {
            return value.stream()
                .map(Right::asCharacter)
                .map(String::valueOf)
                .collect(Collectors.joining());
        }

        public String toString() {
            return serialize();
        }

        /**
         * Performs the theoretic operation of union of this
         * Rfc4314Rights and toAdd Rfc4314Rights.
         *
         * A schematic example: "lr".union("rw") returns "lrw".
         *
         * Implementations must return a new unmodifiable instance of
         * {@link MailboxACL.Rfc4314Rights}.
         *
         * @param toAdd
         * @return union of this and toAdd
         * @throws UnsupportedRightException
         *
         */
        public Rfc4314Rights union(Rfc4314Rights toAdd) throws UnsupportedRightException {
            Preconditions.checkNotNull(toAdd);
            EnumSet<Right> rightUnion = EnumSet.noneOf(Right.class);
            rightUnion.addAll(value);
            rightUnion.addAll(toAdd.value);
            return new Rfc4314Rights(rightUnion);
        }
    }

    /**
     * A utility implementation of
     * {@code Map.Entry<EntryKey, Rfc4314Rights>}.
     */
    public static class Entry extends AbstractMap.SimpleEntry<EntryKey, Rfc4314Rights> {
        public Entry(EntryKey key, Rfc4314Rights value) {
            super(key, value);
        }

        public Entry(String key, String value) throws UnsupportedRightException {
            this(EntryKey.deserialize(key), new Rfc4314Rights(value));
        }
    }

    /**
     * The key used in {@link MailboxACL#getEntries()}. Implementations should
     * override {@link #hashCode()} and {@link #equals(Object)} in such a way
     * that all of {@link #getName()}, {@link #getNameType()} and
     * {@link #isNegative()} are significant.
     *
     */
    public static class EntryKey {
        public static EntryKey createGroup(String name) {
            return new EntryKey(name, NameType.group, false);
        }

        public static EntryKey createGroup(String name, boolean negative) {
            return new EntryKey(name, NameType.group, negative);
        }

        public static EntryKey createUser(String name) {
            return new EntryKey(name, NameType.user, false);
        }

        public static EntryKey createUser(String name, boolean negative) {
            return new EntryKey(name, NameType.user, negative);
        }

        private final String name;
        private final NameType nameType;
        private final boolean negative;

        /**
         * Creates a new instance of SimpleMailboxACLEntryKey from the given
         * serialized {@link String}. It supposes that negative rights are
         * marked with {@link MailboxACL#DEFAULT_NEGATIVE_MARKER} and that
         * groups are marked with {@link MailboxACL#DEFAULT_GROUP_MARKER}.
         * 
         * @param serialized
         */
        public static EntryKey deserialize(String serialized) {
            Preconditions.checkNotNull(serialized, "Cannot parse null");
            Preconditions.checkArgument(!serialized.isEmpty(), "Cannot parse an empty string");

            boolean negative = serialized.charAt(0) == DEFAULT_NEGATIVE_MARKER;
            int nameStart = negative ? 1 : 0;
            boolean isGroup = serialized.charAt(nameStart) == DEFAULT_GROUP_MARKER;
            Optional<NameType> explicitNameType = isGroup ? Optional.of(NameType.group) : Optional.empty();
            String name = isGroup ? serialized.substring(nameStart + 1) : serialized.substring(nameStart);

            if (name.isEmpty()) {
                throw new IllegalStateException("Cannot parse a string with empty name");
            }
            NameType nameType = explicitNameType.orElseGet(() -> computeImplicitNameType(name));

            return new EntryKey(name, nameType, negative);
        }

        private static NameType computeImplicitNameType(String name) {
            boolean isSpecialName = Arrays.stream(SpecialName.values())
                .anyMatch(specialName -> specialName.name().equals(name));
            if (isSpecialName) {
                return NameType.special;
            }
            return NameType.user;
        }

        public EntryKey(String name, NameType nameType, boolean negative) {
            Preconditions.checkNotNull(name, "Provide a name for this " + getClass().getName());
            Preconditions.checkNotNull(nameType, "Provide a nameType for this " + getClass().getName());

            this.name = name;
            this.nameType = nameType;
            this.negative = negative;
        }

        public boolean equals(Object o) {
            if (o instanceof EntryKey) {
                EntryKey other = (EntryKey) o;
                return Objects.equals(this.name, other.getName())
                    && Objects.equals(this.nameType, other.getNameType())
                    && Objects.equals(this.negative, other.isNegative());
            }
            return false;
        }

        /**
         * Returns the name of a user or of a group to which this
         * {@link MailboxACL.MailboxACLEntryKey} applies.
         *
         * @return User name, group name or special name.
         */
        public String getName() {
            return name;
        }

        /**
         * Tells of what type is the name returned by {@link #getName()}.
         *
         * @return type of the name returned by {@link #getName()}
         */
        public NameType getNameType() {
            return nameType;
        }

        public final int hashCode() {
            return Objects.hash(negative, nameType, name);
        }

        /**
         * If true the {@link MailboxACL.MailboxACLRights} returned by
         * {@link MailboxACLEntry#getRights()} should be interpreted as
         * "negative rights" as described in RFC4314: If the identifier "-fred"
         * is granted the "w" right, that indicates that the "w" right is to be
         * removed from users matching the identifier "fred", even though the
         * user "fred" might have the "w" right as a consequence of some other
         * identifier in the ACL.
         *
         * Note that {@link MailboxACLEntry#getName()} does not start with "-"
         * when {@link MailboxACLEntry#getRights()} returns true.
         *
         * @return
         */
        public boolean isNegative() {
            return negative;
        }

        /**
         * Returns a serialized form of this {@link MailboxACL.MailboxACLEntryKey} as a
         * {@link String}. Implementations should choose a consistent way how
         * all of {@link #getName()}, {@link #getNameType()} and
         * {@link #isNegative()} get serialized.
         *
         * RFC4314 sction 2. states: All user name strings accepted by the LOGIN
         * or AUTHENTICATE commands to authenticate to the IMAP server are
         * reserved as identifiers for the corresponding users. Identifiers
         * starting with a dash ("-") are reserved for "negative rights",
         * described below. All other identifier strings are interpreted in an
         * implementation-defined manner.
         *
         * Dovecot and Cyrus mark groups with '$' prefix. See <a
         * href="http://wiki2.dovecot.org/SharedMailboxes/Shared"
         * >http://wiki2.dovecot.org/SharedMailboxes/Shared</a>:
         *
         * <cite>The $group syntax is not a standard, but it is mentioned in RFC
         * 4314 examples and is also understood by at least Cyrus IMAP. Having
         * '-' before the identifier specifies negative rights.</cite>
         *
         * @see MailboxACL#DEFAULT_GROUP_MARKER
         * @see MailboxACL#DEFAULT_NEGATIVE_MARKER
         *
         * @return serialized form as a {@link String}
         */
        public String serialize() {
            String negativePart = negative ? String.valueOf(DEFAULT_NEGATIVE_MARKER) : "";
            String nameTypePart = nameType == NameType.group ? String.valueOf(DEFAULT_GROUP_MARKER) : "";

            return negativePart + nameTypePart + name;
        }

        public String toString() {
            return serialize();
        }
    }


    public static class ACLCommand {
        private final EntryKey key;
        private final EditMode editMode;
        private final Rfc4314Rights rights;

        public ACLCommand(EntryKey key, EditMode editMode, Rfc4314Rights rights) {
            this.key = key;
            this.editMode = editMode;
            this.rights = rights;
        }

        public EntryKey getEntryKey() {
            return key;
        }

        public EditMode getEditMode() {
            return editMode;
        }

        public Rfc4314Rights getRights() {
            return rights;
        }

        public final boolean equals(Object o) {
            if (o instanceof ACLCommand) {
                ACLCommand that = (ACLCommand) o;

                return Objects.equals(this.key, that.key)
                    && Objects.equals(this.editMode, that.editMode)
                    && Objects.equals(this.rights, that.rights);
            }
            return false;
        }

        public final int hashCode() {
            return Objects.hash(key, editMode, rights);
        }
    }

    public static final EntryKey ANYBODY_KEY;
    public static final EntryKey ANYBODY_NEGATIVE_KEY;
    public static final EntryKey AUTHENTICATED_KEY;
    public static final EntryKey AUTHENTICATED_NEGATIVE_KEY;
    public static final MailboxACL EMPTY;

    public static final Rfc4314Rights FULL_RIGHTS;

    public static final Rfc4314Rights NO_RIGHTS;
    public static final MailboxACL OWNER_FULL_ACL;
    public static final MailboxACL OWNER_FULL_EXCEPT_ADMINISTRATION_ACL;

    public static final EntryKey OWNER_KEY;
    public static final EntryKey OWNER_NEGATIVE_KEY;

    static {
        try {
            ANYBODY_KEY = new EntryKey(SpecialName.anybody.name(), NameType.special, false);
            ANYBODY_NEGATIVE_KEY = new EntryKey(SpecialName.anybody.name(), NameType.special, true);
            AUTHENTICATED_KEY = new EntryKey(SpecialName.authenticated.name(), NameType.special, false);
            AUTHENTICATED_NEGATIVE_KEY = new EntryKey(SpecialName.authenticated.name(), NameType.special, true);
            EMPTY = new MailboxACL();
            FULL_RIGHTS =  new Rfc4314Rights(Right.allRights);
            NO_RIGHTS = new Rfc4314Rights();
            OWNER_KEY = new EntryKey(SpecialName.owner.name(), NameType.special, false);
            OWNER_NEGATIVE_KEY = new EntryKey(SpecialName.owner.name(), NameType.special, true);
            OWNER_FULL_ACL = new MailboxACL(new Entry[] { new Entry(MailboxACL.OWNER_KEY, MailboxACL.FULL_RIGHTS) });
            OWNER_FULL_EXCEPT_ADMINISTRATION_ACL = new MailboxACL(new Entry[] { new Entry(MailboxACL.OWNER_KEY, MailboxACL.FULL_RIGHTS.except(new Rfc4314Rights(Right.Administer))) });
        } catch (UnsupportedRightException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<EntryKey, Rfc4314Rights> toMap(Properties props) throws UnsupportedRightException {
        ImmutableMap.Builder<EntryKey, Rfc4314Rights> builder = ImmutableMap.builder();
        if (props != null) {
            for (Map.Entry<?, ?> prop : props.entrySet()) {
                builder.put(EntryKey.deserialize((String) prop.getKey()), new Rfc4314Rights((String) prop.getValue()));
            }
        }
        return builder.build();
    }
    
    private final Map<EntryKey, Rfc4314Rights> entries;

    /**
     * Creates a new instance of SimpleMailboxACL containing no entries.
     * 
     */
    public MailboxACL() {
        this(ImmutableMap.of());
    }

    /**
     * Creates a new instance of SimpleMailboxACL from the given array of
     * entries.
     * 
     * @param entries
     */
    @SafeVarargs
    public MailboxACL(Map.Entry<EntryKey, Rfc4314Rights>... entries) {
        this(ImmutableMap.copyOf(
            Optional.ofNullable(entries)
                .map(array -> Arrays.stream(array)
                    .collect(Guavate.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue)))
            .orElse(ImmutableMap.of())));
    }

    /**
     * Creates a new instance of SimpleMailboxACL from the given {@link Map} of
     * entries.
     *
     * @param entries
     */
    public MailboxACL(Map<EntryKey, Rfc4314Rights> entries) {
        Preconditions.checkNotNull(entries);

        this.entries = ImmutableMap.copyOf(entries);
    }

    /**
     * Creates a new instance of SimpleMailboxACL from {@link Properties}. The
     * keys and values from the <code>props</code> parameter are parsed by the
     * {@link String} constructors of {@link EntryKey} and
     * {@link Rfc4314Rights} respectively.
     * 
     * @param props
     * @throws UnsupportedRightException
     */
    public MailboxACL(Properties props) throws UnsupportedRightException {
        this(toMap(props));
    }

    public boolean equals(Object o) {
        if (o instanceof MailboxACL) {
            MailboxACL acl = (MailboxACL) o;
            return Objects.equals(this.getEntries(), acl.getEntries());
        }
        return false;
    }

    public int hashCode() {
        return Objects.hash(entries);
    }

    /**
     * Apply the given ACL update on current ACL and return the result as a new ACL.
     *
     * @param aclUpdate Update to perform
     * @return Copy of current ACL updated
     * @throws UnsupportedRightException
     */
    public MailboxACL apply(ACLCommand aclUpdate) throws UnsupportedRightException {
        switch (aclUpdate.getEditMode()) {
            case ADD:
                return union(aclUpdate.getEntryKey(), aclUpdate.getRights());
            case REMOVE:
                return except(aclUpdate.getEntryKey(), aclUpdate.getRights());
            case REPLACE:
                return replace(aclUpdate.getEntryKey(), aclUpdate.getRights());
        }
        throw new RuntimeException("Unknown edit mode");
    }

    /**
     * Performs the set theoretic operation of relative complement of toRemove
     * {@link MailboxACL} in this {@link MailboxACL}.
     *
     * A schematic example: "user1:lr;user2:lrwt".except("user1:w;user2:t")
     * returns "user1:lr;user2:lrw".
     *
     * Implementations must return a new unmodifiable instance of
     * {@link MailboxACL}. However, implementations may decide to return this or
     * toRemove parameter value in case the result would be equal to the
     * respective one of those.
     *
     * Implementations must ensure that the result does not contain entries with
     * empty rigths. E.g. "user1:lr;user2:lrwt".except("user1:lr") should return
     * "user2:lrwt" rather than "user1:;user2:lrwt"
     *
     * @param toRemove
     * @return
     * @throws UnsupportedRightException
     */
    public MailboxACL except(MailboxACL other) throws UnsupportedRightException {
        return new MailboxACL(entries.entrySet()
            .stream()
            .map(entry -> Pair.of(
                entry.getKey(),
                except(entry.getValue(), other.getEntries().get(entry.getKey()))))
            .filter(pair -> !pair.getValue().isEmpty())
            .collect(Guavate.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    private Rfc4314Rights except(Rfc4314Rights thisRight, Rfc4314Rights exceptRights) {
        return Optional.ofNullable(exceptRights)
            .map(Throwing.function(thisRight::except))
            .orElse(thisRight);
    }

    public MailboxACL except(EntryKey key, Rfc4314Rights mailboxACLRights) throws UnsupportedRightException {
        return except(new MailboxACL(new Entry(key, mailboxACLRights)));
    }

    /**
     * {@link Map} of entries.
     *
     * @return the entries.
     */
    public Map<EntryKey, Rfc4314Rights> getEntries() {
        return entries;
    }

    /**
     * Replaces the entry corresponding to the given {@code key} with
     * {@code toAdd}link MailboxACLRights}.
     *
     * Implementations must return a new unmodifiable instance of
     * {@link MailboxACL}. However, implementations may decide to return this in
     * case the result would be equal to it.
     *
     * Implementations must ensure that the result does not contain entries with
     * empty rigths. E.g. "user1:lr;user2:lrwt".replace("user1",
     * MailboxACLRights.EMPTY) should return "user2:lrwt" rather than
     * "user1:;user2:lrwt". The same result should be returned by
     * "user1:lr;user2:lrwt".replace("user1", null).
     *
     * @param key
     * @param toAdd
     * @return
     * @throws UnsupportedRightException
     */
    public MailboxACL replace(EntryKey key, Rfc4314Rights replacement) throws UnsupportedRightException {
        if (entries.containsKey(key)) {
            return new MailboxACL(
                entries.entrySet()
                    .stream()
                    .map(entry -> Pair.of(entry.getKey(),
                        entry.getKey().equals(key) ? replacement : entry.getValue()))
                    .filter(pair -> pair.getValue() != null && !pair.getValue().isEmpty())
                    .collect(Guavate.toImmutableMap(Pair::getKey, Pair::getValue)));
        } else {
            return Optional.ofNullable(replacement)
                .map(replacementValue ->  new MailboxACL(
                    ImmutableMap.<EntryKey, Rfc4314Rights>builder()
                        .putAll(entries)
                        .put(key, replacementValue)
                        .build()))
                .orElse(this);
        }
    }

    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("entries", entries)
            .toString();
    }

    /**
     * Performs the set theoretic operation of union of this {@link MailboxACL}
     * and toAdd {@link MailboxACL}.
     *
     * A schematic example:
     * "user1:lr;user2:lrwt".union("user1:at;-$group1:lrwt") returns
     * "user1:alrt;user2:lrwt;-$group1:lrwt".
     *
     * Implementations must return a new unmodifiable instance of
     * {@link MailboxACL}. However, implementations may decide to return this or
     * toAdd parameter value in case the result would be equal to the respective
     * one of those.
     *
     *
     * @param toAdd
     * @return
     * @throws UnsupportedRightException
     */
    public MailboxACL union(MailboxACL other) throws UnsupportedRightException {
        return new MailboxACL(
            Stream.concat(
                    this.entries.entrySet().stream(),
                    other.getEntries().entrySet().stream())
                .collect(Guavate.toImmutableListMultimap(Map.Entry::getKey, Map.Entry::getValue))
                .asMap()
                .entrySet()
                .stream()
                .collect(Guavate.toImmutableMap(Map.Entry::getKey, e -> union(e.getValue()))));
    }

    private Rfc4314Rights union(Collection<Rfc4314Rights> rights) {
        return rights.stream()
            .reduce(
                new Rfc4314Rights(),
                Throwing.binaryOperator(Rfc4314Rights::union));
    }

    public MailboxACL union(EntryKey key, Rfc4314Rights mailboxACLRights) throws UnsupportedRightException {
        return union(new MailboxACL(new Entry(key, mailboxACLRights)));
    }

}
