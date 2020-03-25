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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.james.mailbox.MessageUid;

import com.google.common.base.Objects;

/**
 * Used to define a range of messages by uid.<br>
 * The type of the set should be defined by using an appropriate constructor.
 */
public class MessageRange implements Iterable<MessageUid> {

    public enum Type {
        /** All messages */
        ALL,
        /** A sigle message */
        ONE,
        /** All messages with a uid equal or higher than */
        FROM,
        /** All messages within the given range of uids (inclusive) */
        RANGE
    }

    /**
     * Constructs a range consisting of a single message only.
     * 
     * @param uid
     *            UID of the message
     */
    public static MessageRange one(MessageUid uid) {
        return new MessageRange(Type.ONE, uid, uid);
    }

    /**
     * Constructs a range consisting of all messages.
     */
    public static MessageRange all() {
        return new MessageRange(Type.ALL, MessageUid.MIN_VALUE, MessageUid.MAX_VALUE);
    }

    /**
     * Constructs an inclusive ranges of messages. The parameters will be
     * checked and {@link #from(MessageUid)} used where appropriate.
     * 
     * @param from
     *            first message UID
     * @param to
     *            last message UID
     */
    public static MessageRange range(MessageUid from, MessageUid to) {
        if (to.equals(MessageUid.MAX_VALUE) || to.compareTo(from) < 0) {
            return from(from);
        } else if (from.equals(to)) {
            // from and to is the same so no need to construct a real range
            return one(from);
        } else {
            return new MessageRange(Type.RANGE, from, to);
        }
    }


    /**
     * Constructs an inclusive, open ended range of messages.
     * 
     * @param from
     *            first message UID in range
     */
    public static MessageRange from(MessageUid from) {
        return new MessageRange(Type.FROM, from, MessageUid.MAX_VALUE);
    }


    private final Type type;
    private final MessageUid uidFrom;
    private final MessageUid uidTo;

    protected MessageRange(Type type, MessageUid minValue, MessageUid maxValue) {
        super();
        this.type = type;
        this.uidFrom = minValue;
        this.uidTo = maxValue;
    }

    public Type getType() {
        return type;
    }

    public MessageUid getUidFrom() {
        return uidFrom;
    }

    public MessageUid getUidTo() {
        return uidTo;
    }


    /**
     * Return true if the uid is within the range
     */
    public boolean includes(MessageUid uid) {
        switch (type) {
        case ALL:
            return true;
        case FROM:
            if (getUidFrom().compareTo(uid) <= 0) {
                return true;
            }
            return false;
        case RANGE:
            if (getUidFrom().compareTo(uid) <= 0 &&
                    getUidTo().compareTo(uid) >= 0) {
                return true;
            }
            return false;
        case ONE:
            if (getUidFrom().equals(uid)) {
                return true;
            }
            return false;
        default:
            return false;
        }
    }

    public String toString() {
        return "TYPE: " + type + " UID: " + uidFrom + ":" + uidTo;
    }

    /**
     * Converts the given {@link Collection} of uids to a {@link List} of {@link MessageRange} instances
     * 
     * @param uidsCol
     *          collection of uids to convert
     * @return ranges
     */
    public static List<MessageRange> toRanges(Collection<MessageUid> uidsCol) {
        List<MessageRange> ranges = new ArrayList<>();
        List<MessageUid> uids = new ArrayList<>(uidsCol);
        Collections.sort(uids);
        
        long firstUid = 0;
        int a = 0;
        for (int i = 0; i < uids.size(); i++) {
            long u = uids.get(i).asLong();
            if (i == 0) {
                firstUid =  u;
                if (uids.size() == 1) {
                    ranges.add(MessageUid.of(firstUid).toRange());
                }
            } else {
                if ((firstUid + a + 1) != u) {
                    ranges.add(MessageRange.range(MessageUid.of(firstUid), MessageUid.of(firstUid + a)));
                    
                    // set the next first uid and reset the counter
                    firstUid = u;
                    a = 0;
                    if (uids.size() <= i + 1) {
                        ranges.add(MessageUid.of(firstUid).toRange());
                    }
                } else {
                    a++;
                    // Handle uids which are in sequence. See MAILBOX-56
                    if (uids.size() <= i + 1) {
                        ranges.add(MessageRange.range(MessageUid.of(firstUid), MessageUid.of(firstUid + a)));
                        break;
                    } 
                }
            }
        }
        return ranges;
    }
    
    
    /**
     * Return a read-only {@link Iterator} which contains all uid which fall in the specified range.
     */
    @Override
    public Iterator<MessageUid> iterator() {
        return new RangeIterator(getUidFrom(), getUidTo());
    }
    
    private static final class RangeIterator implements Iterator<MessageUid> {

        private final long to;
        private long current;
        
        public RangeIterator(MessageUid from, MessageUid to) {
            this.to = to.asLong();
            this.current = from.asLong();
        }
        
        @Override
        public boolean hasNext() {
            return current <= to;
        }

        @Override
        public MessageUid next() {
            if (hasNext()) {
                return MessageUid.of(current++);
            } else {
                throw new NoSuchElementException("Max uid of " + to + " was reached before");
            }
        }

        @Override
        public void remove() {
            throw new java.lang.UnsupportedOperationException("Read-Only");
        }
        
    }
    
    
    /**
     * Tries to split the given {@link MessageRange} to a {@link List} of {@link MessageRange}'s which 
     * select only a max amount of items. This only work for {@link MessageRange}'s with {@link Type} of 
     * {@link Type#RANGE}.
     */
    public List<MessageRange> split(int maxItems) {
        List<MessageRange> ranges = new ArrayList<>();
        if (getType() == Type.RANGE) {
            long from = getUidFrom().asLong();
            long to = getUidTo().asLong();
            long realTo = to;
            while (from <= realTo) {
                to = Math.min(from + maxItems - 1, realTo);
                if (from == to) {
                    ranges.add(MessageUid.of(from).toRange());
                } else {
                    ranges.add(MessageRange.range(MessageUid.of(from), MessageUid.of(to)));
                }
                
                from = to + 1;
            }
        } else {
            ranges.add(this);
        }
        return ranges;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type, uidFrom, uidTo);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MessageRange) {
            MessageRange other = (MessageRange) obj;
            return Objects.equal(this.type, other.type) &&
                    Objects.equal(this.uidFrom, other.uidFrom) &&
                    Objects.equal(this.uidTo, other.uidTo);
        }
        return false;
    }
}
