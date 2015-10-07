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

/**
 * Used to define a range of messages by uid.<br>
 * The type of the set should be defined by using an appropriate constructor.
 */
public class MessageRange implements Iterable<Long>{

    public enum Type {
        /** All messages */
        ALL,
        /** A sigle message */
        ONE,
        /** All messages with a uid equal or higher than */
        FROM,
        /** All messagse within the given range of uids (inclusive) */
        RANGE
    }

    public static final long NOT_A_UID = -1;

    public static final long MAX_UID = Long.MAX_VALUE;

    /**
     * Constructs a range consisting of a single message only.
     * 
     * @param uid
     *            UID of the message
     * @return not null
     */
    public static MessageRange one(long uid) {
        final MessageRange result = new MessageRange(Type.ONE, uid, uid);
        return result;
    }

    /**
     * Constructs a range consisting of all messages.
     * 
     * @return not null
     */
    public static MessageRange all() {
        final MessageRange result = new MessageRange(Type.ALL, NOT_A_UID, MAX_UID);
        return result;
    }

    /**
     * Constructs an inclusive ranges of messages. The parameters will be
     * checked and {@link #from(long)} used where appropriate.
     * 
     * @param from
     *            first message UID
     * @param to
     *            last message UID
     * @return not null
     */
    public static MessageRange range(long from, long to) {
        final MessageRange result;
        if (to == Long.MAX_VALUE || to < from) {
            to = NOT_A_UID;
            result = from(from);
        } else if (from == to) {
            // from and to is the same so no need to construct a real range
            result = one(from);
        } else {
            result = new MessageRange(Type.RANGE, from, to);
        }
        return result;
    }


    /**
     * Constructs an inclusive, open ended range of messages.
     * 
     * @param from
     *            first message UID in range
     * @return not null
     */
    public static MessageRange from(long from) {
        return new MessageRange(Type.FROM, from, MAX_UID);
    }


    private final Type type;

    private final long uidFrom;

    private final long uidTo;

    protected MessageRange(final Type type, final long uidFrom, final long uidTo) {
        super();
        this.type = type;
        this.uidFrom = uidFrom;
        this.uidTo = uidTo;
    }

    public Type getType() {
        return type;
    }

    public long getUidFrom() {
        return uidFrom;
    }

    public long getUidTo() {
        return uidTo;
    }


    /**
     * Return true if the uid is within the range
     * 
     * @param uid
     * @return withinRange
     */
    public boolean includes(long uid) {
        switch (type) {
        case ALL:
            return true;
        case FROM:
            if (uid > getUidFrom()) {
                return true;
            }
        case RANGE:
            if (uid >= getUidFrom() && uid <= getUidTo()) {
                return true;
            }
        case ONE:
            if (getUidFrom() == uid) {
                return true;
            }
        default:
            break;
        }
        return false;
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
    public static List<MessageRange> toRanges(Collection<Long> uidsCol) {
        List<MessageRange> ranges = new ArrayList<MessageRange>();
        List<Long> uids = new ArrayList<Long>(uidsCol);
        Collections.sort(uids);
        
        long firstUid = 0;
        int a = 0;
        for (int i = 0; i < uids.size(); i++) {
            long u = uids.get(i);
            if (i == 0) {
                firstUid =  u;
                if (uids.size() == 1) {
                    ranges.add(MessageRange.one(firstUid));
                }
            } else {
                if ((firstUid + a +1) != u) {
                    ranges.add(MessageRange.range(firstUid, firstUid + a));
                    
                    // set the next first uid and reset the counter
                    firstUid = u;
                    a = 0;
                    if (uids.size() <= i +1) {
                        ranges.add(MessageRange.one(firstUid));
                    }
                } else {
                    a++;
                    // Handle uids which are in sequence. See MAILBOX-56
                    if (uids.size() <= i +1) {
                        ranges.add(MessageRange.range(firstUid, firstUid +a));
                        break;
                    } 
                }
            }
        }
        return ranges;
    }
    
    
    /**
     * Return a read-only {@link Iterator} which contains all uid which fail in the specified range.
     * 
     * @return rangeIt
     */
    @Override
    public Iterator<Long> iterator() {
        long from = getUidFrom();
        if (from == NOT_A_UID) {
            from = 1;
        }
        long to = getUidTo();
        if (to == NOT_A_UID) {
            to = Long.MAX_VALUE;
        }
        return new RangeIterator(from, to);
    }
    
    /**
     * {@link Iterator} of a range of msn/uid
     *
     */
    private final class RangeIterator implements Iterator<Long> {

        private long to;
        private long current;
        
        public RangeIterator(long from, long to) {
            this.to = to;
            this.current = from;
        }
        
        @Override
        public boolean hasNext() {
            return current <= to;
        }

        @Override
        public Long next() {
            if (hasNext()) {
                return current++;
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
     * 
     * @param maxItems
     * @return ranges
     */
    public List<MessageRange> split( int maxItems) {
        List<MessageRange> ranges = new ArrayList<MessageRange>();
        if (getType() == Type.RANGE) {
            long from = getUidFrom();
            long to = getUidTo();
            long realTo = to;
            while(from <= realTo) {
                if (from + maxItems  -1 < realTo) {
                    to = from + maxItems -1;
                } else {
                    to = realTo;
                }
                if (from == to) {
                    ranges.add(MessageRange.one(from));
                } else {
                    ranges.add(MessageRange.range(from, to));
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
        final int prime = 31;
        int result = 1;
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        result = prime * result + (int) (uidFrom ^ (uidFrom >>> 32));
        result = prime * result + (int) (uidTo ^ (uidTo >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MessageRange other = (MessageRange) obj;
        if (type != other.type)
            return false;
        if (uidFrom != other.uidFrom)
            return false;
        if (uidTo != other.uidTo)
            return false;
        return true;
    }
}
