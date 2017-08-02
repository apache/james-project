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

package org.apache.james.imap.api.message.request;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.UidRange;

/**
 * Atom key used by a search. Build instances by factory methods.
 */
public final class SearchKey {

    // NUMBERS

    public static final int TYPE_SEQUENCE_SET = 1;
    public static final int TYPE_UID = 2;

    // NO PARAMETERS
    public static final int TYPE_ALL = 3;

    public static final int TYPE_ANSWERED = 4;

    public static final int TYPE_DELETED = 5;

    public static final int TYPE_DRAFT = 6;

    public static final int TYPE_FLAGGED = 7;

    public static final int TYPE_NEW = 8;

    public static final int TYPE_OLD = 9;

    public static final int TYPE_RECENT = 10;

    public static final int TYPE_SEEN = 11;

    public static final int TYPE_UNANSWERED = 12;

    public static final int TYPE_UNDELETED = 13;

    public static final int TYPE_UNDRAFT = 14;

    public static final int TYPE_UNFLAGGED = 15;

    public static final int TYPE_UNSEEN = 16;

    // ONE VALUE
    public static final int TYPE_BCC = 17;

    public static final int TYPE_BODY = 18;

    public static final int TYPE_CC = 19;

    public static final int TYPE_FROM = 20;

    public static final int TYPE_KEYWORD = 21;

    public static final int TYPE_SUBJECT = 22;

    public static final int TYPE_TEXT = 23;

    public static final int TYPE_TO = 24;

    public static final int TYPE_UNKEYWORD = 25;

    // ONE DATE
    public static final int TYPE_BEFORE = 26;

    public static final int TYPE_ON = 27;

    public static final int TYPE_SENTBEFORE = 28;

    public static final int TYPE_SENTON = 29;

    public static final int TYPE_SENTSINCE = 30;

    public static final int TYPE_SINCE = 31;

    // FIELD VALUE
    public static final int TYPE_HEADER = 32;

    // ONE NUMBER
    public static final int TYPE_LARGER = 33;

    public static final int TYPE_SMALLER = 34;

    // NOT
    public static final int TYPE_NOT = 35;

    // OR
    public static final int TYPE_OR = 36;

    // AND
    public static final int TYPE_AND = 37;

    public static final int TYPE_YOUNGER = 38;
    public static final int TYPE_OLDER = 39;

    public static final int TYPE_MODSEQ = 40;

    private static final SearchKey UNSEEN = new SearchKey(TYPE_UNSEEN, null, null, 0, null, null, null, null, -1, -1);

    private static final SearchKey UNFLAGGED = new SearchKey(TYPE_UNFLAGGED, null, null, 0, null, null, null, null, -1, -1);

    private static final SearchKey UNDRAFT = new SearchKey(TYPE_UNDRAFT, null, null, 0, null, null, null, null, -1, -1);

    private static final SearchKey UNDELETED = new SearchKey(TYPE_UNDELETED, null, null, 0, null, null, null, null, -1, -1);

    private static final SearchKey UNANSWERED = new SearchKey(TYPE_UNANSWERED, null, null, 0, null, null, null, null, -1, -1);

    private static final SearchKey SEEN = new SearchKey(TYPE_SEEN, null, null, 0, null, null, null, null, -1, -1);

    private static final SearchKey RECENT = new SearchKey(TYPE_RECENT, null, null, 0, null, null, null, null, -1, -1);

    private static final SearchKey OLD = new SearchKey(TYPE_OLD, null, null, 0, null, null, null, null, -1, -1);

    private static final SearchKey NEW = new SearchKey(TYPE_NEW, null, null, 0, null, null, null, null, -1, -1);

    private static final SearchKey FLAGGED = new SearchKey(TYPE_FLAGGED, null, null, 0, null, null, null, null, -1, -1);

    private static final SearchKey DRAFT = new SearchKey(TYPE_DRAFT, null, null, 0, null, null, null, null, -1, -1);

    private static final SearchKey DELETED = new SearchKey(TYPE_DELETED, null, null, 0, null, null, null, null, -1, -1);

    private static final SearchKey ANSWERED = new SearchKey(TYPE_ANSWERED, null, null, 0, null, null, null, null, -1, -1);

    private static final SearchKey ALL = new SearchKey(TYPE_ALL, null, null, 0, null, null, null, null, -1, -1);

    // NUMBERS
    public static SearchKey buildSequenceSet(IdRange[] ids) {
        return new SearchKey(TYPE_SEQUENCE_SET, null, null, 0, null, null, null, ids, -1, -1);
    }

    public static SearchKey buildUidSet(UidRange[] ids) {
        return new SearchKey(TYPE_UID, null, null, 0, null, null, ids, null, -1, -1);
    }

    // NO PARAMETERS
    public static SearchKey buildAll() {
        return ALL;
    }

    public static SearchKey buildAnswered() {
        return ANSWERED;
    }

    public static SearchKey buildDeleted() {
        return DELETED;
    }

    public static SearchKey buildDraft() {
        return DRAFT;
    }

    public static SearchKey buildFlagged() {
        return FLAGGED;
    }

    public static SearchKey buildNew() {
        return NEW;
    }

    public static SearchKey buildOld() {
        return OLD;
    }

    public static SearchKey buildRecent() {
        return RECENT;
    }

    public static SearchKey buildSeen() {
        return SEEN;
    }

    public static SearchKey buildUnanswered() {
        return UNANSWERED;
    }

    public static SearchKey buildUndeleted() {
        return UNDELETED;
    }

    public static SearchKey buildUndraft() {
        return UNDRAFT;
    }

    public static SearchKey buildUnflagged() {
        return UNFLAGGED;
    }

    public static SearchKey buildUnseen() {
        return UNSEEN;
    }

    // ONE VALUE
    public static SearchKey buildBcc(String value) {
        return new SearchKey(TYPE_BCC, null, null, 0, null, value, null, null, -1, -1);
    }

    public static SearchKey buildBody(String value) {
        return new SearchKey(TYPE_BODY, null, null, 0, null, value, null, null, -1, -1);
    }

    public static SearchKey buildCc(String value) {
        return new SearchKey(TYPE_CC, null, null, 0, null, value, null, null, -1, -1);
    }

    public static SearchKey buildFrom(String value) {
        return new SearchKey(TYPE_FROM, null, null, 0, null, value, null, null, -1, -1);
    }

    public static SearchKey buildKeyword(String value) {
        return new SearchKey(TYPE_KEYWORD, null, null, 0, null, value, null, null, -1, -1);
    }

    public static SearchKey buildSubject(String value) {
        return new SearchKey(TYPE_SUBJECT, null, null, 0, null, value, null, null, -1, -1);
    }

    public static SearchKey buildText(String value) {
        return new SearchKey(TYPE_TEXT, null, null, 0, null, value, null, null, -1, -1);
    }

    public static SearchKey buildTo(String value) {
        return new SearchKey(TYPE_TO, null, null, 0, null, value, null, null, -1, -1);
    }

    public static SearchKey buildUnkeyword(String value) {
        return new SearchKey(TYPE_UNKEYWORD, null, null, 0, null, value, null, null, -1, -1);
    }
    // ONE DATE
    public static SearchKey buildYounger(long seconds) {
        return new SearchKey(TYPE_YOUNGER, null, null, 0, null, null, null, null, seconds, -1);
    }

    public static SearchKey buildOlder(long seconds) {
        return new SearchKey(TYPE_OLDER, null, null, 0, null, null, null, null, seconds, -1);
    }

    
    // ONE DATE
    public static SearchKey buildBefore(DayMonthYear date) {
        return new SearchKey(TYPE_BEFORE, date, null, 0, null, null, null, null, -1, -1);
    }

    public static SearchKey buildOn(DayMonthYear date) {
        return new SearchKey(TYPE_ON, date, null, 0, null, null, null, null, -1, -1);
    }

    public static SearchKey buildSentBefore(DayMonthYear date) {
        return new SearchKey(TYPE_SENTBEFORE, date, null, 0, null, null, null, null, -1, -1);
    }

    public static SearchKey buildSentOn(DayMonthYear date) {
        return new SearchKey(TYPE_SENTON, date, null, 0, null, null, null, null, -1, -1);
    }

    public static SearchKey buildSentSince(DayMonthYear date) {
        return new SearchKey(TYPE_SENTSINCE, date, null, 0, null, null, null, null, -1, -1);
    }

    public static SearchKey buildSince(DayMonthYear date) {
        return new SearchKey(TYPE_SINCE, date, null, 0, null, null, null, null, -1, -1);
    }

    // FIELD VALUE
    public static SearchKey buildHeader(String name, String value) {
        return new SearchKey(TYPE_HEADER, null, null, 0, name, value, null, null, -1, -1);
    }

    // ONE NUMBER
    public static SearchKey buildLarger(long size) {
        return new SearchKey(TYPE_LARGER, null, null, size, null, null, null, null, -1, -1);
    }

    public static SearchKey buildSmaller(long size) {
        return new SearchKey(TYPE_SMALLER, null, null, size, null, null, null, null, -1, -1);
    }

    // NOT
    public static SearchKey buildNot(SearchKey key) {
        final List<SearchKey> keys = new ArrayList<>();
        keys.add(key);
        return new SearchKey(TYPE_NOT, null, keys, 0, null, null, null, null, -1, -1);
    }

    // OR
    public static SearchKey buildOr(SearchKey keyOne, SearchKey keyTwo) {
        final List<SearchKey> keys = new ArrayList<>();
        keys.add(keyOne);
        keys.add(keyTwo);
        return new SearchKey(TYPE_OR, null, keys, 0, null, null, null, null, -1, -1);
    }

    /**
     * Componses an <code>AND</code> key from given keys.
     * 
     * @param keys
     *            <code>List</code> of {@link SearchKey}'s composing this key
     * @return <code>SearchKey</code>, not null
     */
    public static SearchKey buildAnd(List<SearchKey> keys) {
        return new SearchKey(TYPE_AND, null, keys, 0, null, null, null, null, -1, -1);
    }

    public static SearchKey buildModSeq(long modSeq) {
        return new SearchKey(TYPE_ANSWERED, null, null, 0, null, null, null, null, -1, modSeq);
    }
    private final int type;

    private final DayMonthYear date;

    private final List<SearchKey> keys;

    private final long size;

    private final String name;

    private final String value;

    private final IdRange[] sequence;
    
    private final UidRange[] uids;

    private final long seconds;

    private final long modSeq;
    
    private SearchKey(int type, DayMonthYear date, List<SearchKey> keys, long number, String name, String value, UidRange[] uids, IdRange[] sequence, long seconds, long modSeq) {
        this.type = type;
        this.date = date;
        this.keys = keys;
        this.size = number;
        this.name = name;
        this.value = value;
        this.seconds = seconds;
        this.modSeq = modSeq;
        this.uids = uids;
        this.sequence = sequence;
    }
    
    /**
     * Gets a date value to be search upon.
     * 
     * @return the date when: {@link #TYPE_BEFORE}, {@link #TYPE_ON},
     *         {@link #TYPE_SENTBEFORE}, {@link #TYPE_SENTON},
     *         {@link #TYPE_SENTSINCE}, {@link #TYPE_SINCE}; otherwise null
     */
    public DayMonthYear getDate() {
        return date;
    }
    
    /**
     * Return the search seconds
     * 
     * @return seconds
     */
    public long getSeconds() {
        return seconds;
    }

    /**
     * Gets sequence numbers.
     * 
     * @return msn when {@link #TYPE_SEQUENCE_SET}, uids when {@link #TYPE_UID},
     *         null otherwise
     */
    public IdRange[] getSequenceNumbers() {
        return sequence;
    }

    public UidRange[] getUidRanges() {
        return uids;
    }
    
    /**
     * Gets the field name.
     * 
     * @return the field name when {@link #TYPE_HEADER}, null otherwise
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the size searched for.
     * 
     * @return the size when {@link #TYPE_LARGER} or {@link #TYPE_SMALLER},
     *         otherwise 0
     */
    public long getSize() {
        return size;
    }

    /**
     * Gets key two.
     * 
     * @return <code>List</code> of <code>SearchKey</code>'s when
     *         {@link #TYPE_OR}, {@link #TYPE_AND} or {@link #TYPE_NOT}
     *         otherwise null
     */
    public List<SearchKey> getKeys() {
        return keys;
    }

    /**
     * Gets the type of key.
     * 
     * @return the type
     */
    public int getType() {
        return type;
    }

    /**
     * Gets the value to be searched for.
     * 
     * @return the value, or null when this type is not associated with a value.
     */
    public String getValue() {
        return value;
    }

    /**
     * Gets the size searched for.
     * 
     * @return the size when {@link #TYPE_MODSEQ}
     *         otherwise -1
     */
    public long getModSeq() {
        return modSeq;
    }
    /**
     * @see java.lang.Object#hashCode()
     */
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + ((date == null) ? 0 : date.hashCode());
        result = PRIME * result + ((name == null) ? 0 : name.hashCode());
        if (sequence != null) {
            result = PRIME * result + sequence.length;
        }
        result = PRIME * result + (int) (size ^ (size >>> 32));
        result = PRIME * result + ((keys == null) ? 0 : keys.hashCode());
        result = PRIME * result + type;
        result = PRIME * result + ((value == null) ? 0 : value.hashCode());
        return result;
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final SearchKey other = (SearchKey) obj;
        if (date == null) {
            if (other.date != null)
                return false;
        } else if (!date.equals(other.date))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (!Arrays.equals(sequence, other.sequence))
            return false;
        if (size != other.size)
            return false;
        if (keys == null) {
            if (other.keys != null)
                return false;
        } else if (!keys.equals(other.keys))
            return false;
        if (type != other.type)
            return false;
        if (value == null) {
            if (other.value != null)
                return false;
        } else if (!value.equals(other.value))
            return false;
        return true;
    }
}