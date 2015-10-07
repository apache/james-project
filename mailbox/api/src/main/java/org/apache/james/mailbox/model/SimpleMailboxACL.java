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

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.james.mailbox.exception.UnsupportedRightException;

/**
 * Default implementation of {@link MailboxACL}.
 * 
 */
public class SimpleMailboxACL implements MailboxACL {

    /**
     * Supports only the Standard Rights of RFC 4314 section 2.1. The rights are
     * stored as single bits in 32 bit int {@link #value} field.
     */
    public static class Rfc4314Rights implements MailboxACLRights {
        /**
         * See RFC 4314 section 2.1.1. Obsolete Rights.
         */
        public enum CompatibilityMode {
            ck_detx, ckx_det, NO_COMPATIBILITY
        }

        private class Rfc4314RightsIterator implements Iterator<MailboxACLRight> {

            int position = 0;

            public Rfc4314RightsIterator() {
                super();
                nextPostion();
            }

            @Override
            public boolean hasNext() {
                return position < FIELD_COUNT;
            }

            @Override
            public MailboxACLRight next() {
                if (!hasNext()) {
                    throw new IndexOutOfBoundsException("No next element at position " + position + " from " + FIELD_COUNT + " in " + Rfc4314RightsIterator.class.getName());
                }
                MailboxACLRight result = indexRightLookup[position];
                position++;
                nextPostion();
                return result;
            }

            /**
             */
            private void nextPostion() {
                while (position < FIELD_COUNT && (value & (1 << position)) == 0) {
                    position++;
                }
            }

            @Override
            public void remove() {
                throw new java.lang.UnsupportedOperationException("Cannot remove rights through this " + Rfc4314RightsIterator.class.getName());
            }

        }

        /**
         * a - administer (perform SETACL/DELETEACL/GETACL/LISTRIGHTS)
         * 
         */
        public static final char a_Administer = 'a';

        static final int a_Administer_MASK = 1;
        public static final MailboxACLRight a_Administer_RIGHT = new SimpleMailboxACL.SimpleMailboxACLRight(a_Administer);
        public static final char c_ObsoleteCreate = 'c';
        public static final char d_ObsoleteDelete = 'd';
        /**
         * e - perform EXPUNGE and expunge as a part of CLOSE
         * 
         */
        public static final char e_PerformExpunge = 'e';
        static final int e_PerformExpunge_MASK = 1 << 1;
        public static final MailboxACLRight e_PerformExpunge_RIGHT = new SimpleMailboxACL.SimpleMailboxACLRight(e_PerformExpunge);
        public static final int EMPTY_MASK = 0;
        public static final int FIELD_COUNT = 11;
        /**
         * i - insert (perform APPEND, COPY into mailbox)
         * 
         */
        public static final char i_Insert = 'i';
        static final int i_Insert_MASK = 1 << 2;

        public static final MailboxACLRight i_Insert_RIGHT = new SimpleMailboxACL.SimpleMailboxACLRight(i_Insert);
        private static final char[] indexFlagLookup;
        private static final MailboxACLRight[] indexRightLookup;
        /**
         * k - create mailboxes (CREATE new sub-mailboxes in any
         * implementation-defined hierarchy, parent mailbox for the new mailbox
         * name in RENAME)
         * 
         */
        public static final char k_CreateMailbox = 'k';
        static final int k_CreateMailbox_MASK = 1 << 3;
        public static final MailboxACLRight k_CreateMailbox_RIGHT = new SimpleMailboxACL.SimpleMailboxACLRight(k_CreateMailbox);
        /**
         * l - lookup (mailbox is visible to LIST/LSUB commands, SUBSCRIBE
         * mailbox)
         * 
         */
        public static final char l_Lookup = 'l';
        static final int l_Lookup_MASK = 1 << 4;
        public static final MailboxACLRight l_Lookup_RIGHT = new SimpleMailboxACL.SimpleMailboxACLRight(l_Lookup);
        /**
         * p - post (send mail to submission address for mailbox, not enforced
         * by IMAP4 itself)
         * 
         */
        public static final char p_Post = 'p';
        static final int p_Post_MASK = 1 << 5;
        public static final MailboxACLRight p_Post_RIGHT = new SimpleMailboxACL.SimpleMailboxACLRight(p_Post);

        /**
         * r - read (SELECT the mailbox, perform STATUS)
         * 
         */
        public static final char r_Read = 'r';
        static final int r_Read_MASK = 1 << 6;

        public static final MailboxACLRight r_Read_RIGHT = new SimpleMailboxACL.SimpleMailboxACLRight(r_Read);
        /**
         * s - keep seen/unseen information across sessions (set or clear \SEEN
         * flag via STORE, also set \SEEN during APPEND/COPY/ FETCH BODY[...])
         * 
         */
        public static final char s_WriteSeenFlag = 's';

        static final int s_WriteSeenFlag_MASK = 1 << 7;

        public static final MailboxACLRight s_WriteSeenFlag_RIGHT = new SimpleMailboxACL.SimpleMailboxACLRight(s_WriteSeenFlag);

        public static final char t_DeleteMessages = 't';

        /**
         * t - delete messages (set or clear \DELETED flag via STORE, set
         * \DELETED flag during APPEND/COPY)
         * 
         */
        static final int t_DeleteMessages_MASK = 1 << 8;
        public static final MailboxACLRight t_DeleteMessages_RIGHT = new SimpleMailboxACL.SimpleMailboxACLRight(t_DeleteMessages);
        /**
         * w - write (set or clear flags other than \SEEN and \DELETED via
         * STORE, also set them during APPEND/COPY)
         * 
         */
        public static final char w_Write = 'w';
        static final int w_Write_MASK = 1 << 9;
        public static final MailboxACLRight w_Write_RIGHT = new SimpleMailboxACL.SimpleMailboxACLRight(w_Write);
        /**
         * x - delete mailbox (DELETE mailbox, old mailbox name in RENAME)
         * 
         */
        public static final char x_DeleteMailbox = 'x';
        static final int x_DeleteMailbox_MASK = 1 << 10;
        public static final MailboxACLRight x_DeleteMailbox_RIGHT = new SimpleMailboxACL.SimpleMailboxACLRight(x_DeleteMailbox);
        static {
            indexFlagLookup = new char[] { a_Administer, e_PerformExpunge, i_Insert, k_CreateMailbox, l_Lookup, p_Post, r_Read, s_WriteSeenFlag, t_DeleteMessages, w_Write, x_DeleteMailbox };
            indexRightLookup = new MailboxACLRight[] { a_Administer_RIGHT, e_PerformExpunge_RIGHT, i_Insert_RIGHT, k_CreateMailbox_RIGHT, l_Lookup_RIGHT, p_Post_RIGHT, r_Read_RIGHT, s_WriteSeenFlag_RIGHT, t_DeleteMessages_RIGHT, w_Write_RIGHT, x_DeleteMailbox_RIGHT };
        }

        private static int flagMaskLookup(char flag) throws UnsupportedRightException {
            switch (flag) {
            case a_Administer:
                return a_Administer_MASK;
            case e_PerformExpunge:
                return e_PerformExpunge_MASK;
            case i_Insert:
                return i_Insert_MASK;
            case k_CreateMailbox:
                return k_CreateMailbox_MASK;
            case l_Lookup:
                return l_Lookup_MASK;
            case p_Post:
                return p_Post_MASK;
            case r_Read:
                return r_Read_MASK;
            case s_WriteSeenFlag:
                return s_WriteSeenFlag_MASK;
            case t_DeleteMessages:
                return t_DeleteMessages_MASK;
            case w_Write:
                return w_Write_MASK;
            case x_DeleteMailbox:
                return x_DeleteMailbox_MASK;
            default:
                throw new UnsupportedRightException(flag);
            }
        }

        /**
         * See RFC 4314 section 2.1.1. Obsolete Rights.
         */
        private CompatibilityMode compatibilityMode = CompatibilityMode.ckx_det;

        /**
         * 32 bit <code>int</code> to store the rights.
         */
        private final int value;

        private Rfc4314Rights() {
            this.value = EMPTY_MASK;
        }

        public Rfc4314Rights(boolean canAdminister, boolean canCreateMailbox, boolean canDeleteMailbox, boolean canDeleteMessages, boolean canInsert, boolean canLookup, boolean canPerformExpunge, boolean canPost, boolean canRead, boolean canWrite, boolean canWriteSeenFlag) {
            super();
            int v = 0;

            if (canAdminister) {
                v |= a_Administer_MASK;
            }
            if (canCreateMailbox) {
                v |= k_CreateMailbox_MASK;
            }
            if (canDeleteMailbox) {
                v |= x_DeleteMailbox_MASK;
            }
            if (canDeleteMessages) {
                v |= t_DeleteMessages_MASK;
            }
            if (canInsert) {
                v |= i_Insert_MASK;
            }
            if (canLookup) {
                v |= l_Lookup_MASK;
            }
            if (canPerformExpunge) {
                v |= e_PerformExpunge_MASK;
            }
            if (canPost) {
                v |= p_Post_MASK;
            }
            if (canRead) {
                v |= r_Read_MASK;
            }
            if (canWrite) {
                v |= w_Write_MASK;
            }
            if (canWriteSeenFlag) {
                v |= s_WriteSeenFlag_MASK;
            }

            this.value = v;

        }

        public Rfc4314Rights(int value) throws UnsupportedRightException {
            if ((value >> FIELD_COUNT) != 0) {
                throw new UnsupportedRightException();
            }
            this.value = value;
        }
        
        public Rfc4314Rights(MailboxACLRight right) throws UnsupportedRightException {
            this.value = flagMaskLookup(right.getValue());
        }

        public Rfc4314Rights(String serializedRfc4314Rights) throws UnsupportedRightException {
            int v = 0;

            for (int i = 0; i < serializedRfc4314Rights.length(); i++) {
                char flag = serializedRfc4314Rights.charAt(i);
                switch (flag) {
                case c_ObsoleteCreate:
                    switch (compatibilityMode) {
                    case ck_detx:
                        v |= k_CreateMailbox_MASK;
                        break;
                    case ckx_det:
                        v |= k_CreateMailbox_MASK;
                        v |= x_DeleteMailbox_MASK;
                        break;
                    case NO_COMPATIBILITY:
                        throw new UnsupportedRightException(flag);
                    default:
                        throw new IllegalStateException("Unexpected enum member: " + CompatibilityMode.class.getName() + "." + compatibilityMode.name());
                    }
                    break;
                case d_ObsoleteDelete:
                    switch (compatibilityMode) {
                    case ck_detx:
                        v |= e_PerformExpunge_MASK;
                        v |= t_DeleteMessages_MASK;
                        v |= x_DeleteMailbox_MASK;
                        break;
                    case ckx_det:
                        v |= e_PerformExpunge_MASK;
                        v |= t_DeleteMessages_MASK;
                        break;
                    case NO_COMPATIBILITY:
                        throw new UnsupportedRightException(flag);
                    default:
                        throw new IllegalStateException("Unexpected enum member: " + CompatibilityMode.class.getName() + "." + compatibilityMode.name());
                    }
                    break;
                default:
                    v |= flagMaskLookup(flag);
                }
            }
            this.value = v;

        }

        public boolean contains(char flag) throws UnsupportedRightException {

            switch (flag) {
            case c_ObsoleteCreate:
                switch (compatibilityMode) {
                case ck_detx:
                    return (value & k_CreateMailbox_MASK) != 0;
                case ckx_det:
                    return (value & (k_CreateMailbox_MASK | x_DeleteMailbox_MASK)) != 0;
                case NO_COMPATIBILITY:
                    throw new UnsupportedRightException(flag);
                default:
                    throw new IllegalStateException("Unexpected enum member: " + CompatibilityMode.class.getName() + "." + compatibilityMode.name());
                }
            case d_ObsoleteDelete:
                switch (compatibilityMode) {
                case ck_detx:
                    return (value & (e_PerformExpunge_MASK | t_DeleteMessages_MASK | x_DeleteMailbox_MASK)) != 0;
                case ckx_det:
                    return (value & (e_PerformExpunge_MASK | t_DeleteMessages_MASK)) != 0;
                case NO_COMPATIBILITY:
                    throw new UnsupportedRightException(flag);
                default:
                    throw new IllegalStateException("Unexpected enum member: " + CompatibilityMode.class.getName() + "." + compatibilityMode.name());
                }
            default:
                return (value & flagMaskLookup(flag)) != 0;
            }
        }

        /** 
         * @see
         * org.apache.james.mailbox.MailboxACL.MailboxACLRights#contains(org
         * .apache.james.mailbox.MailboxACL.MailboxACLRight)
         */
        @Override
        public boolean contains(MailboxACLRight right) throws UnsupportedRightException {
            return contains(right.getValue());
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Rfc4314Rights) {
                return this.value == ((Rfc4314Rights) o).value;
            } else if (o instanceof MailboxACLRights) {
                try {
                    return this.value == new Rfc4314Rights(((MailboxACLRights) o).serialize()).value;
                } catch (UnsupportedRightException e) {
                    throw new RuntimeException(e);
                }
            } else {
                return false;
            }
        }

        /** 
         * @see
         * org.apache.james.mailbox.MailboxACL.MailboxACLRights#except(org.apache
         * .james.mailbox.MailboxACL.MailboxACLRights)
         */
        @Override
        public MailboxACLRights except(MailboxACLRights toRemove) throws UnsupportedRightException {
            if (this.value == EMPTY_MASK || toRemove == null || toRemove.isEmpty()) {
                /* nothing to remove */
                return this;
            } else if (toRemove instanceof Rfc4314Rights) {
                Rfc4314Rights other = (Rfc4314Rights) toRemove;
                if (other.value == EMPTY_MASK) {
                    /* toRemove is an identity element */
                    return this;
                } else {
                    return new Rfc4314Rights(this.value & (~((other).value)));
                }
            } else {
                return new Rfc4314Rights(this.value & (~(new Rfc4314Rights(toRemove.serialize()).value)));
            }
        }

        public int getValue() {
            return value;
        }

        /**
         * Returns {@link #value}.
         * 
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            return value;
        }

        /**
         * @see org.apache.james.mailbox.model.MailboxACL.MailboxACLRights#isEmpty()
         */
        @Override
        public boolean isEmpty() {
            return value == EMPTY_MASK;
        }

        /** 
         * @see
         * org.apache.james.mailbox.MailboxACL.MailboxACLRights#isSupported(
         * org.apache.james.mailbox.MailboxACL.MailboxACLRight)
         */
        @Override
        public boolean isSupported(MailboxACLRight right) {
            try {
                contains(right.getValue());
                return true;
            } catch (UnsupportedRightException e) {
                return false;
            }
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Iterable#iterator()
         */
        @Override
        public Iterator<MailboxACLRight> iterator() {
            return new Rfc4314RightsIterator();
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.apache.james.mailbox.MailboxACL.MailboxACLRights#serialize()
         */
        @Override
        public String serialize() {
            StringBuilder result = new StringBuilder(FIELD_COUNT);
            for (int i = 0; i < FIELD_COUNT; i++) {
                if ((value & (1 << i)) != 0) {
                    result.append(indexFlagLookup[i]);
                }
            }
            return result.toString();
        }

        /**
         * Returns {@link #serialize()}
         * 
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            return serialize();
        }

        /*
         * (non-Javadoc)
         * 
         * @see
         * org.apache.james.mailbox.MailboxACL.MailboxACLRights#union(org.apache
         * .james.mailbox.MailboxACL.MailboxACLRights)
         */
        @Override
        public MailboxACLRights union(MailboxACLRights toAdd) throws UnsupportedRightException {
            if (this.value == EMPTY_MASK) {
                /* this is an identity element */
                return toAdd;
            } else if (toAdd instanceof Rfc4314Rights) {
                Rfc4314Rights other = (Rfc4314Rights) toAdd;
                if (other.value == EMPTY_MASK) {
                    /* toAdd is an identity element */
                    return this;
                } else {
                    return new Rfc4314Rights(this.value | other.value);
                }
            } else {
                return new Rfc4314Rights(this.value | new Rfc4314Rights(toAdd.serialize()).value);
            }
        }

    }

    /**
     * A utility implementation of
     * {@code Map.Entry<MailboxACLEntryKey, MailboxACLRights>}.
     */
    public static class SimpleMailboxACLEntry implements Map.Entry<MailboxACLEntryKey, MailboxACLRights> {
        private final MailboxACLEntryKey key;

        private final MailboxACLRights value;

        public SimpleMailboxACLEntry(MailboxACLEntryKey key, MailboxACLRights value) {
            super();
            this.key = key;
            this.value = value;
        }
        public SimpleMailboxACLEntry(String key, String value) throws UnsupportedRightException {
            this(new SimpleMailboxACLEntryKey(key), new Rfc4314Rights(value));
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.util.Map.Entry#getKey()
         */
        @Override
        public MailboxACLEntryKey getKey() {
            return key;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.util.Map.Entry#getValue()
         */
        @Override
        public MailboxACLRights getValue() {
            return value;
        }

        /**
         * Unsupported.
         * 
         * @see java.util.Map.Entry#setValue(java.lang.Object)
         */
        @Override
        public MailboxACLRights setValue(MailboxACLRights value) {
            throw new java.lang.UnsupportedOperationException("Fields of " + MailboxACLRights.class.getName() + " are read only.");
        }

    }

    /**
     * Default implementation of {@link MailboxACLEntryKey}.
     */
    public static class SimpleMailboxACLEntryKey implements MailboxACLEntryKey {
        public static SimpleMailboxACLEntryKey createGroup(String name) {
            return new SimpleMailboxACLEntryKey(name, NameType.group, false);
        }

        public static SimpleMailboxACLEntryKey createGroup(String name, boolean negative) {
            return new SimpleMailboxACLEntryKey(name, NameType.group, negative);
        }

        public static SimpleMailboxACLEntryKey createUser(String name) {
            return new SimpleMailboxACLEntryKey(name, NameType.user, false);
        }

        public static SimpleMailboxACLEntryKey createUser(String name, boolean negative) {
            return new SimpleMailboxACLEntryKey(name, NameType.user, negative);
        }

        private final int hash;
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
        public SimpleMailboxACLEntryKey(String serialized) {

            if (serialized == null) {
                throw new IllegalStateException("Cannot parse null to a " + getClass().getName());
            }
            if (serialized.length() == 0) {
                throw new IllegalStateException("Cannot parse an empty string to a " + getClass().getName());
            }
            int start = 0;
            if (serialized.charAt(start) == DEFAULT_NEGATIVE_MARKER) {
                negative = true;
                start++;
            } else {
                negative = false;
            }
            if (serialized.charAt(start) == DEFAULT_GROUP_MARKER) {
                nameType = NameType.group;
                start++;
                name = serialized.substring(start);
                if (name.length() == 0) {
                    throw new IllegalStateException("Cannot parse a string with empty name to a " + getClass().getName());
                }
            } else {
                name = serialized.substring(start);
                if (name.length() == 0) {
                    throw new IllegalStateException("Cannot parse a string with empty name to a " + getClass().getName());
                }
                NameType nt = NameType.user;
                for (SpecialName specialName : SpecialName.values()) {
                    if (specialName.name().equals(name)) {
                        nt = NameType.special;
                        break;
                    }
                }
                this.nameType = nt;
            }

            this.hash = hash();

        }

        public SimpleMailboxACLEntryKey(String name, NameType nameType, boolean negative) {
            super();
            if (name == null) {
                throw new NullPointerException("Provide a name for this " + getClass().getName());
            }
            if (nameType == null) {
                throw new NullPointerException("Provide a nameType for this " + getClass().getName());
            }
            this.name = name;
            this.nameType = nameType;
            this.negative = negative;
            this.hash = hash();
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof MailboxACLEntryKey) {
                MailboxACLEntryKey other = (MailboxACLEntryKey) o;
                return this.name.equals(other.getName()) && this.nameType.equals(other.getNameType()) && this.negative == other.isNegative();
            } else {
                return false;
            }
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.apache.james.mailbox.MailboxACL.MailboxACLEntryKey#getName()
         */
        public String getName() {
            return name;
        }

        /*
         * (non-Javadoc)
         * 
         * @see
         * org.apache.james.mailbox.MailboxACL.MailboxACLEntryKey#getNameType()
         */
        public NameType getNameType() {
            return nameType;
        }

        private int hash() {
            final int PRIME = 31;
            int hash = negative ? 1 : 0;
            hash = PRIME * hash + nameType.hashCode();
            hash = PRIME * hash + name.hashCode();
            return hash;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        /*
         * (non-Javadoc)
         * 
         * @see
         * org.apache.james.mailbox.MailboxACL.MailboxACLEntryKey#isNegative()
         */
        public boolean isNegative() {
            return negative;
        }

        /**
         * Serializes this {@link SimpleMailboxACLEntryKey} using
         * {@link MailboxACL#DEFAULT_NEGATIVE_MARKER} and
         * {@link MailboxACL#DEFAULT_GROUP_MARKER}.
         * 
         * @see org.apache.james.mailbox.model.MailboxACL.MailboxACLEntryKey#serialize()
         */
        @Override
        public String serialize() {
            if (!negative) {
                switch (nameType) {
                case special:
                case user:
                    return name;
                case group:
                    return new StringBuilder(name.length() + 1).append(DEFAULT_GROUP_MARKER).append(name).toString();
                default:
                    throw new IllegalStateException();
                }
            } else {
                StringBuilder result = new StringBuilder(name.length() + 2).append(DEFAULT_NEGATIVE_MARKER);
                switch (nameType) {
                case special:
                case user:
                    break;
                case group:
                    result.append(DEFAULT_GROUP_MARKER);
                    break;
                default:
                    throw new IllegalStateException();
                }
                return result.append(name).toString();
            }
        }

        @Override
        public String toString() {
            return serialize();
        }

    }

    /**
     * Default implementation of {@link MailboxACLRight}.
     */
    public static final class SimpleMailboxACLRight implements MailboxACLRight {
        private final char value;

        public SimpleMailboxACLRight(char value) {
            super();
            this.value = value;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object o) {
            if (o instanceof MailboxACLRight) {
                return ((MailboxACLRight) o).getValue() == this.value;
            }
            return false;
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.apache.james.mailbox.MailboxACL.MailboxACLRight#getValue()
         */
        @Override
        public char getValue() {
            return value;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            return (int) value;
        }

        /**
         * Returns <code>String.valueOf(value)</code>.
         * 
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            return String.valueOf(value);
        }

    }

    public static class SimpleMailboxACLCommand implements MailboxACLCommand {
        private MailboxACLEntryKey key;
        private EditMode editMode;
        private MailboxACLRights rights;

        public SimpleMailboxACLCommand(MailboxACLEntryKey key, EditMode editMode, MailboxACLRights rights) {
            this.key = key;
            this.editMode = editMode;
            this.rights = rights;
        }

        @Override
        public MailboxACLEntryKey getEntryKey() {
            return key;
        }

        @Override
        public EditMode getEditMode() {
            return editMode;
        }

        @Override
        public MailboxACLRights getRights() {
            return rights;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SimpleMailboxACLCommand)) return false;

            SimpleMailboxACLCommand that = (SimpleMailboxACLCommand) o;

            if (key != null ? !key.equals(that.key) : that.key != null) return false;
            if (editMode != that.editMode) return false;
            return !(rights != null ? !rights.equals(that.rights) : that.rights != null);

        }

        @Override
        public int hashCode() {
            int result = key != null ? key.hashCode() : 0;
            result = 31 * result + (editMode != null ? editMode.hashCode() : 0);
            result = 31 * result + (rights != null ? rights.hashCode() : 0);
            return result;
        }
    }

    public static final MailboxACLEntryKey ANYBODY_KEY;
    public static final MailboxACLEntryKey ANYBODY_NEGATIVE_KEY;
    public static final MailboxACLEntryKey AUTHENTICATED_KEY;
    public static final MailboxACLEntryKey AUTHENTICATED_NEGATIVE_KEY;
    public static final MailboxACL EMPTY;

    public static final MailboxACLRights FULL_RIGHTS;

    public static final MailboxACLRights NO_RIGHTS;
    public static final MailboxACL OWNER_FULL_ACL;
    public static final MailboxACL OWNER_FULL_EXCEPT_ADMINISTRATION_ACL;

    public static final MailboxACLEntryKey OWNER_KEY;
    public static final MailboxACLEntryKey OWNER_NEGATIVE_KEY;

    static {
        try {
            ANYBODY_KEY = new SimpleMailboxACLEntryKey(SpecialName.anybody.name(), NameType.special, false);
            ANYBODY_NEGATIVE_KEY = new SimpleMailboxACLEntryKey(SpecialName.anybody.name(), NameType.special, true);
            AUTHENTICATED_KEY = new SimpleMailboxACLEntryKey(SpecialName.authenticated.name(), NameType.special, false);
            AUTHENTICATED_NEGATIVE_KEY = new SimpleMailboxACLEntryKey(SpecialName.authenticated.name(), NameType.special, true);
            EMPTY = new SimpleMailboxACL();
            FULL_RIGHTS =  new Rfc4314Rights(true, true, true, true, true, true, true, true, true, true, true);
            NO_RIGHTS = new Rfc4314Rights();
            OWNER_KEY = new SimpleMailboxACLEntryKey(SpecialName.owner.name(), NameType.special, false);
            OWNER_NEGATIVE_KEY = new SimpleMailboxACLEntryKey(SpecialName.owner.name(), NameType.special, true);
            OWNER_FULL_ACL = new SimpleMailboxACL(new SimpleMailboxACL.SimpleMailboxACLEntry[] { new SimpleMailboxACL.SimpleMailboxACLEntry(SimpleMailboxACL.OWNER_KEY, SimpleMailboxACL.FULL_RIGHTS) });
            OWNER_FULL_EXCEPT_ADMINISTRATION_ACL = new SimpleMailboxACL(new SimpleMailboxACL.SimpleMailboxACLEntry[] { new SimpleMailboxACL.SimpleMailboxACLEntry(SimpleMailboxACL.OWNER_KEY, SimpleMailboxACL.FULL_RIGHTS.except(new Rfc4314Rights(Rfc4314Rights.a_Administer_MASK))) });
        } catch (UnsupportedRightException e) {
            throw new RuntimeException(e);
        }
    }
    
    private final Map<MailboxACLEntryKey, MailboxACLRights> entries;

    /**
     * Creates a new instance of SimpleMailboxACL containing no entries.
     * 
     */
    public SimpleMailboxACL() {
        this.entries = Collections.emptyMap();
    }

    /**
     * Creates a new instance of SimpleMailboxACL from the given array of
     * entries.
     * 
     * @param entries
     */
    public SimpleMailboxACL(Map.Entry<MailboxACLEntryKey, MailboxACLRights>[] entries) {
        if (entries != null) {
            Map<MailboxACLEntryKey, MailboxACLRights> m = new HashMap<MailboxACLEntryKey, MailboxACLRights>(entries.length + entries.length / 2 + 1);
            for (Entry<MailboxACLEntryKey, MailboxACLRights> en : entries) {
                m.put(en.getKey(), en.getValue());
            }
            this.entries = Collections.unmodifiableMap(m);
        } else {
            this.entries = Collections.emptyMap();
        }
    }

    /**
     * Creates a new instance of SimpleMailboxACL from the given {@link Map} of
     * entries.
     * 
     * @param entries
     */
    public SimpleMailboxACL(Map<MailboxACLEntryKey, MailboxACLRights> entries) {
        if (entries != null && entries.size() > 0) {
            Map<MailboxACLEntryKey, MailboxACLRights> m = new HashMap<MailboxACLEntryKey, MailboxACLRights>(entries.size() + entries.size() / 2 + 1);
            for (Entry<MailboxACLEntryKey, MailboxACLRights> en : entries.entrySet()) {
                m.put(en.getKey(), en.getValue());
            }
            this.entries = Collections.unmodifiableMap(m);
        } else {
            this.entries = Collections.emptyMap();
        }
    }

    /**
     * Creates a new instance of SimpleMailboxACL.
     * <code>unmodifiableEntries</code> parameter is supposed to be umodifiable
     * already.
     * 
     * @param unmodifiableEntries
     * @param dummy
     *            just to be different from {@link #SimpleMailboxACL(Map)}.
     */
    private SimpleMailboxACL(Map<MailboxACLEntryKey, MailboxACLRights> unmodifiableEntries, boolean dummy) {
        this.entries = unmodifiableEntries;
    }

    /**
     * Creates a new instance of SimpleMailboxACL from {@link Properties}. The
     * keys and values from the <code>props</code> parameter are parsed by the
     * {@link String} constructors of {@link SimpleMailboxACLEntryKey} and
     * {@link Rfc4314Rights} respectively.
     * 
     * @param props
     * @throws UnsupportedRightException
     */
    public SimpleMailboxACL(Properties props) throws UnsupportedRightException {
        super();

        Map<MailboxACLEntryKey, MailboxACLRights> m = new HashMap<MailboxACLEntryKey, MailboxACLRights>(props.size() + props.size() / 2 + 1);

        if (props != null) {
            for (Map.Entry<Object, Object> prop : props.entrySet()) {
                m.put(new SimpleMailboxACLEntryKey((String) prop.getKey()), new Rfc4314Rights((String) prop.getValue()));
            }
        }

        entries = Collections.unmodifiableMap(m);
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object o) {
        if (o instanceof MailboxACL) {
            MailboxACL acl = (MailboxACL) o;
            Map<MailboxACLEntryKey, MailboxACLRights> ens = acl.getEntries();
            return entries == ens || (entries != null && entries.equals(ens));
        }
        return false;
    }

    @Override
    public MailboxACL apply(MailboxACLCommand aclUpdate) throws UnsupportedRightException {
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
     * @see org.apache.james.mailbox.MailboxACL#except(org.apache.james.mailbox.MailboxACL)
     */
    @Override
    public MailboxACL except(MailboxACL other) throws UnsupportedRightException {
        if (entries.size() == 0) {
            return this;
        } else {
            Map<MailboxACLEntryKey, MailboxACLRights> otherEntries = other.getEntries();
            Map<MailboxACLEntryKey, MailboxACLRights> resultEntries = new HashMap<MailboxACLEntryKey, MailboxACLRights>(this.entries);
            for (Entry<MailboxACLEntryKey, MailboxACLRights> otherEntry : otherEntries.entrySet()) {
                MailboxACLEntryKey key = otherEntry.getKey();
                MailboxACLRights thisRights = resultEntries.get(key);
                if (thisRights == null) {
                    /* nothing to diff */
                } else {
                    /* diff */
                    MailboxACLRights resultRights = thisRights.except(otherEntry.getValue());
                    if (!resultRights.isEmpty()) {
                        resultEntries.put(key, resultRights);
                    }
                    else {
                        resultEntries.remove(key);
                    }
                }
            }
            return new SimpleMailboxACL(Collections.unmodifiableMap(resultEntries), true);
        }
    }
    
    /**
     * @see org.apache.james.mailbox.model.MailboxACL#except(org.apache.james.mailbox.model.MailboxACL.MailboxACLEntryKey, org.apache.james.mailbox.model.MailboxACL.MailboxACLRights)
     */
    public MailboxACL except(MailboxACLEntryKey key, MailboxACLRights mailboxACLRights) throws UnsupportedRightException {
        Map<MailboxACLEntryKey, MailboxACLRights> resultEntries = new HashMap<MailboxACLEntryKey, MailboxACLRights>(this.entries);
        MailboxACLRights thisRights = resultEntries.get(key);
        if (thisRights == null) {
            /* nothing to diff */
        } else {
            /* diff */
            MailboxACLRights resultRights = thisRights.except(mailboxACLRights);
            if (!resultRights.isEmpty()) {
                resultEntries.put(key, resultRights);
            }
            else {
                resultEntries.remove(key);
            }
        }
        return new SimpleMailboxACL(Collections.unmodifiableMap(resultEntries), true);
    }

    /**
     * @see org.apache.james.mailbox.MailboxACL#getEntries()
     */
    @Override
    public Map<MailboxACLEntryKey, MailboxACLRights> getEntries() {
        return entries;
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return entries == null ? 0 : entries.hashCode();
    }

    /**
     * @see org.apache.james.mailbox.model.MailboxACL#replace(org.apache.james.mailbox.model.MailboxACL.MailboxACLEntryKey, org.apache.james.mailbox.model.MailboxACL.MailboxACLRights)
     */
    @Override
    public MailboxACL replace(MailboxACLEntryKey key, MailboxACLRights replacement) throws UnsupportedRightException {
        Map<MailboxACLEntryKey, MailboxACLRights> resultEntries = new HashMap<MailboxACLEntryKey, MailboxACLRights>(this.entries);
        if (replacement == null || replacement.isEmpty()) {
            resultEntries.remove(key);
        } else {
            resultEntries.put(key, replacement);
        }
        return new SimpleMailboxACL(Collections.unmodifiableMap(resultEntries), true);
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return entries == null ? "" : entries.toString();
    }

    /**
     * @see org.apache.james.mailbox.MailboxACL#union(org.apache.james.mailbox.MailboxACL)
     */
    @Override
    public MailboxACL union(MailboxACL other) throws UnsupportedRightException {
        Map<MailboxACLEntryKey, MailboxACLRights> otherEntries = other.getEntries();
        if (otherEntries.size() == 0) {
            return this;
        } else if (entries.size() == 0) {
            return other;
        } else {
            int cnt = otherEntries.size() + entries.size();
            Map<MailboxACLEntryKey, MailboxACLRights> resultEntries = new HashMap<MailboxACLEntryKey, MailboxACLRights>(cnt + cnt / 2 + 1);
            for (Entry<MailboxACLEntryKey, MailboxACLRights> otherEntry : otherEntries.entrySet()) {
                MailboxACLEntryKey key = otherEntry.getKey();
                MailboxACLRights thisRights = entries.get(key);
                if (thisRights == null) {
                    /* nothing to union */
                    resultEntries.put(key, otherEntry.getValue());
                } else {
                    /* union */
                    resultEntries.put(key, otherEntry.getValue().union(thisRights));
                }
            }
            /* let us check what we have missed in the previous loop */
            for (Entry<MailboxACLEntryKey, MailboxACLRights> thisEntry : entries.entrySet()) {
                MailboxACLEntryKey key = thisEntry.getKey();
                if (!resultEntries.containsKey(key)) {
                    resultEntries.put(key, thisEntry.getValue());
                }
            }
            return new SimpleMailboxACL(Collections.unmodifiableMap(resultEntries), true);
        }
    }
    
    /**
     * @see org.apache.james.mailbox.model.MailboxACL#union(org.apache.james.mailbox.model.MailboxACL.MailboxACLEntryKey, org.apache.james.mailbox.model.MailboxACL.MailboxACLRights)
     */
    public MailboxACL union(MailboxACLEntryKey key, MailboxACLRights mailboxACLRights) throws UnsupportedRightException {
        Map<MailboxACLEntryKey, MailboxACLRights> resultEntries = new HashMap<MailboxACLEntryKey, MailboxACLRights>(this.entries);
        MailboxACLRights thisRights = resultEntries.get(key);
        if (thisRights == null) {
            /* nothing to union */
            resultEntries.put(key, mailboxACLRights);
        } else {
            /* union */
            resultEntries.put(key, thisRights.union(mailboxACLRights));
        }
        return new SimpleMailboxACL(Collections.unmodifiableMap(resultEntries), true);
    }

}
