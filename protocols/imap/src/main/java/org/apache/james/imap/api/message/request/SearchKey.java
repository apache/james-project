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

import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_ALL;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_AND;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_ANSWERED;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_BCC;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_BEFORE;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_BODY;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_CC;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_DELETED;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_DRAFT;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_FLAGGED;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_FROM;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_HEADER;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_KEYWORD;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_LARGER;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_MODSEQ;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_NEW;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_NOT;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_OLD;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_OLDER;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_ON;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_OR;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_RECENT;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_SEEN;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_SENTBEFORE;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_SENTON;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_SENTSINCE;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_SEQUENCE_SET;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_SINCE;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_SMALLER;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_SUBJECT;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_TEXT;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_TO;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_UID;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_UNANSWERED;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_UNDELETED;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_UNDRAFT;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_UNFLAGGED;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_UNKEYWORD;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_UNSEEN;
import static org.apache.james.imap.api.message.request.SearchKey.Type.TYPE_YOUNGER;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.UidRange;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

/**
 * Atom key used by a search. Build instances by factory methods.
 */
public final class SearchKey {

    public enum Type {
        TYPE_SEQUENCE_SET,
        TYPE_UID,
        TYPE_ALL,
        TYPE_ANSWERED,
        TYPE_DELETED,
        TYPE_DRAFT,
        TYPE_FLAGGED,
        TYPE_NEW,
        TYPE_OLD,
        TYPE_RECENT,
        TYPE_SEEN,
        TYPE_UNANSWERED,
        TYPE_UNDELETED,
        TYPE_UNDRAFT,
        TYPE_UNFLAGGED,
        TYPE_UNSEEN,
        TYPE_BCC,
        TYPE_BODY,
        TYPE_CC,
        TYPE_FROM,
        TYPE_KEYWORD,
        TYPE_SUBJECT,
        TYPE_TEXT,
        TYPE_TO,
        TYPE_UNKEYWORD,
        TYPE_BEFORE,
        TYPE_ON,
        TYPE_SENTBEFORE,
        TYPE_SENTON,
        TYPE_SENTSINCE,
        TYPE_SINCE,
        TYPE_HEADER,
        TYPE_LARGER,
        TYPE_SMALLER,
        TYPE_NOT,
        TYPE_OR,
        TYPE_AND,
        TYPE_YOUNGER,
        TYPE_OLDER,
        TYPE_MODSEQ
    }

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
        return new SearchKey(TYPE_MODSEQ, null, null, 0, null, null, null, null, -1, modSeq);
    }
    
    private final Type type;

    private final DayMonthYear date;

    private final List<SearchKey> keys;

    private final long size;

    private final String name;

    private final String value;

    private final IdRange[] sequence;
    
    private final UidRange[] uids;

    private final long seconds;

    private final long modSeq;
    
    private SearchKey(Type type, DayMonthYear date, List<SearchKey> keys, long number, String name, String value, UidRange[] uids, IdRange[] sequence, long seconds, long modSeq) {
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
     * @return the date when: TYPE_BEFORE, TYPE_ON,
     *         TYPE_SENTBEFORE, TYPE_SENTON, TYPE_SENTSINCE, TYPE_SINCE, otherwise null
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
     * @return msn when TYPE_SEQUENCE_SET, uids when TYPE_UID,
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
     * @return the field name when TYPE_HEADER, null otherwise
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the size searched for.
     * 
     * @return the size when TYPE_LARGER or TYPE_SMALLER,
     *         otherwise 0
     */
    public long getSize() {
        return size;
    }

    /**
     * Gets key two.
     * 
     * @return <code>List</code> of <code>SearchKey</code>'s when
     *         TYPE_OR, TYPE_AND or TYPE_NOT
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
    public Type getType() {
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
     * @return the size when TYPE_MODSEQ
     *         otherwise -1
     */
    public long getModSeq() {
        return modSeq;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof SearchKey) {
            SearchKey searchKey = (SearchKey) o;

            return Objects.equals(this.type, searchKey.type)
                && Objects.equals(this.size, searchKey.size)
                && Objects.equals(this.seconds, searchKey.seconds)
                && Objects.equals(this.modSeq, searchKey.modSeq)
                && Objects.equals(this.date, searchKey.date)
                && Objects.equals(this.keys, searchKey.keys)
                && Objects.equals(this.name, searchKey.name)
                && Objects.equals(this.value, searchKey.value)
                && Arrays.equals(this.sequence, searchKey.sequence)
                && Arrays.equals(this.uids, searchKey.uids);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(type, date, keys, size, name, value,
            Arrays.hashCode(sequence), Arrays.hashCode(uids), seconds, modSeq);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("type", type)
            .add("date", date)
            .add("size", size)
            .add("value", value)
            .add("seconds", seconds)
            .add("modSeq", modSeq)
            .add("uids", Arrays.toString(uids))
            .add("sequences", Arrays.toString(sequence))
            .add("keys", Optional.ofNullable(keys).map(ImmutableList::copyOf))
            .toString();
    }
}
