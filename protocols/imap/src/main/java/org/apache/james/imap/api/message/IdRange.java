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

package org.apache.james.imap.api.message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.apache.james.mailbox.model.MessageRange;

import com.google.common.collect.ImmutableList;

/**
 * Represents a range of UID or MSN values.
 */
public final class IdRange implements Iterable<Long>, Comparable<IdRange> {
    /**
     * {@link Iterator} of a range of msn/uid
     */
    private final class RangeIterator implements Iterator<Long> {

        private final long to;
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
                throw new NoSuchElementException("Highest id of " + to + " was reached before");
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Read-Only");
        }

    }

    /**
     * Utility method which will copy the given {@link List} and try to merge
     * the {@link IdRange} in the copy before return it.
     *
     * @return mergedRanges
     */
    public static List<IdRange> mergeRanges(List<IdRange> ranges) {
        List<IdRange> copy = new ArrayList<>(ranges);
        Collections.sort(copy);

        boolean lastUid = false;

        for (int i = 0; i < copy.size() - 1; i++) {
            IdRange current = copy.get(i);
            IdRange next = copy.get(i + 1);
            if (next.getLowVal() == Long.MAX_VALUE && next.getHighVal() == Long.MAX_VALUE) {
                if (lastUid) {
                    copy.remove(next);
                    i--;
                } else {
                    lastUid = true;
                }
            } else {
                // Make sure we handle the "*" and "*:*" correctly and don't
                // remove ranges by error. See IMAP-289
                if ((current.getLowVal() != Long.MAX_VALUE && current.getHighVal() != Long.MAX_VALUE) && (current.getHighVal() >= next.getLowVal() - 1)) {
                    if (next.getHighVal() > current.getHighVal()) {
                        current.setHighVal(next.getHighVal());
                    }
                    // remove the merged id range and decrease the count
                    copy.remove(next);
                    i--;
                }
            }

        }
        return copy;
    }

    public static String toString(IdRange[] ranges) {
        return Optional.ofNullable(ranges)
            .map(ImmutableList::copyOf)
            .toString();
    }

    public static IdRange from(MessageRange messageRange) {
        return new IdRange(
            messageRange.getUidFrom().asLong(),
            messageRange.getUidTo().asLong());
    }

    private final long lowVal;
    private long highVal;

    public IdRange(long singleVal) {
        lowVal = singleVal;
        highVal = singleVal;
    }

    public IdRange(long lowVal, long highVal) {
        if (lowVal > highVal) {
            throw new IllegalArgumentException("LowVal must be <= HighVal");
        }
        this.lowVal = lowVal;
        this.highVal = highVal;
    }

    public long getLowVal() {
        return lowVal;
    }

    public long getHighVal() {
        return highVal;
    }

    public void setHighVal(long highVal) {
        if (lowVal > highVal) {
            throw new IllegalArgumentException("HighVal must be >= LowVal");
        }
        this.highVal = highVal;
    }

    /**
     * Renders text suitable for logging.
     * 
     * @return a <code>String</code> representation of this object.
     */
    public String toString() {
        return "IdRange ( " + this.lowVal + "->" + this.highVal + " )";
    }

    public String getFormattedString() {
        if (this.lowVal == this.highVal) {
            return Long.toString(this.lowVal);
        } else {
            return this.lowVal + ":" + this.highVal;
        }
    }

    /**
     * Return a read-only {@link Iterator} which contains all msn/uid which fail in the specified range.
     */
    @Override
    public Iterator<Long> iterator() {
        long from = getLowVal();
        if (from == Long.MAX_VALUE) {
            from = 1;
        }
        long to = getHighVal();
        return new RangeIterator(from, to);
    }

    @Override
    public int compareTo(IdRange range2) {
        // Correctly sort and respect "*" and "*:*" ranges. See IMAP-289
        if (getLowVal() == Long.MAX_VALUE && getHighVal() == Long.MAX_VALUE && range2.getLowVal() == Long.MAX_VALUE && range2.getHighVal() == Long.MAX_VALUE) {
            return 0;
        }
        if (getLowVal() == Long.MAX_VALUE && getHighVal() == Long.MAX_VALUE) {
            return 1;
        } else if (range2.getLowVal() == Long.MAX_VALUE && range2.getHighVal() == Long.MAX_VALUE) {
            return -1;
        } else {
            return (int) (getLowVal() - range2.getLowVal());
        }
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + (int) (highVal ^ (highVal >>> 32));
        result = PRIME * result + (int) (lowVal ^ (lowVal >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final IdRange other = (IdRange) obj;
        if (highVal != other.highVal) {
            return false;
        }
        if (lowVal != other.lowVal) {
            return false;
        }
        return true;
    }

}
