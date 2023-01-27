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

package org.apache.james.imap.processor.base;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.NullableMessageSequenceNumber;

import com.google.common.annotations.VisibleForTesting;

import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongComparators;

public class UidMsnConverter {
    private static final int FIRST_MSN = 1;
    private static final long INTEGER_MAX_VALUE = Integer.MAX_VALUE;

    @VisibleForTesting final LongArrayList uids;
    @VisibleForTesting final IntArrayList uidsAsInts;
    @VisibleForTesting boolean usesInts = true;

    public UidMsnConverter() {
        this.uids = new LongArrayList();
        this.uidsAsInts = new IntArrayList();
    }

    public synchronized void addAll(List<MessageUid> addedUids) {
        addAllUnSynchronized(addedUids);
    }

    private void addAllUnSynchronized(List<MessageUid> addedUids) {
        if (usesInts) {
            if (uidsAsInts.isEmpty()) {
                // Avoids intermediary tree structure
                addAllToEmptyIntStructure(addedUids);
            } else {
                addAllToNonEmptyIntStructure(addedUids);
            }
        } else {
            if (uids.isEmpty()) {
                // Avoids intermediary tree structure
                addAllToEmptyLongStructure(addedUids);
            } else {
                addAllToNonEmptyLongStructure(addedUids);
            }
        }
    }

    private void addAllToNonEmptyLongStructure(List<MessageUid> addedUids) {
        LongAVLTreeSet tmp = new LongAVLTreeSet(uids);
        for (MessageUid uid : addedUids) {
            tmp.add(uid.asLong());
        }
        uids.clear();
        uids.addAll(tmp);
    }

    private void addAllToEmptyLongStructure(List<MessageUid> addedUids) {
        uids.ensureCapacity(addedUids.size());
        for (MessageUid uid : addedUids) {
            uids.add(uid.asLong());
        }
        uids.sort(LongComparators.NATURAL_COMPARATOR);
    }

    private void addAllToNonEmptyIntStructure(List<MessageUid> addedUids) {
        IntAVLTreeSet tmp = new IntAVLTreeSet(uidsAsInts);
        for (MessageUid uid : addedUids) {
            if (uid.asLong() > INTEGER_MAX_VALUE) {
                switchToLongs();
                addAllUnSynchronized(addedUids);
                return;
            }
            tmp.add((int) uid.asLong());
        }
        uidsAsInts.clear();
        uidsAsInts.addAll(tmp);
    }

    private void addAllToEmptyIntStructure(List<MessageUid> addedUids) {
        uidsAsInts.ensureCapacity(addedUids.size());
        for (MessageUid uid : addedUids) {
            if (uid.asLong() > INTEGER_MAX_VALUE) {
                uidsAsInts.clear();
                switchToLongs();
                addAllUnSynchronized(addedUids);
                return;
            }
            uidsAsInts.add((int) uid.asLong());
        }
        uidsAsInts.sort(IntComparators.NATURAL_COMPARATOR);
    }

    private void switchToLongs() {
        usesInts = false;
        uids.ensureCapacity(uidsAsInts.size());
        for (int i = 0; i < uidsAsInts.size(); i++) {
            uids.add(uidsAsInts.getInt(i));
        }
    }

    public synchronized NullableMessageSequenceNumber getMsn(MessageUid uid) {
        return getMsnUnsynchronized(uid);
    }

    private NullableMessageSequenceNumber getMsnUnsynchronized(MessageUid uid) {
        if (usesInts) {
            if (uid.asLong() > INTEGER_MAX_VALUE) {
                return NullableMessageSequenceNumber.noMessage();
            }
            int position = Arrays.binarySearch(uidsAsInts.elements(), 0, uidsAsInts.size(), (int) uid.asLong());
            if (position < 0) {
                return NullableMessageSequenceNumber.noMessage();
            }
            return NullableMessageSequenceNumber.of(position + 1);
        } else {
            int position =  Arrays.binarySearch(uids.elements(), 0, uids.size(), uid.asLong());
            if (position < 0) {
                return NullableMessageSequenceNumber.noMessage();
            }
            return NullableMessageSequenceNumber.of(position + 1);
        }
    }

    public synchronized Optional<MessageUid> getUid(int msn) {
        if (usesInts) {
            if (msn <= uidsAsInts.size() && msn > 0) {
                return Optional.of(MessageUid.of(uidsAsInts.getInt(msn - 1)));
            }
        } else {
            if (msn <= uids.size() && msn > 0) {
                return Optional.of(MessageUid.of(uids.getLong(msn - 1)));
            }
        }
        return Optional.empty();
    }

    public synchronized Optional<MessageUid> getLastUid() {
        if (uidsAsInts.isEmpty() && uids.isEmpty()) {
            return Optional.empty();
        }
        return getUid(getLastMsn());
    }

    public synchronized Optional<MessageUid> getFirstUid() {
        return getUid(FIRST_MSN);
    }

    public synchronized int getNumMessage() {
        if (usesInts) {
            return uidsAsInts.size();
        } else {
            return uids.size();
        }
    }

    public synchronized void remove(MessageUid uid) {
        removeUnsynchronized(uid);
    }

    private void removeUnsynchronized(MessageUid uid) {
        if (usesInts) {
            if (uid.asLong() > INTEGER_MAX_VALUE) {
                return;
            }
            int index = Arrays.binarySearch(uidsAsInts.elements(), 0, uidsAsInts.size(), (int) uid.asLong());
            if (index >= 0) {
                uidsAsInts.removeInt(index);
            }
        } else {
            int index = Arrays.binarySearch(uids.elements(), 0, uids.size(), (int) uid.asLong());
            if (index >= 0) {
                uids.removeLong(index);
            }
        }
    }

    public synchronized NullableMessageSequenceNumber getAndRemove(MessageUid uid) {
        NullableMessageSequenceNumber result = getMsnUnsynchronized(uid);
        removeUnsynchronized(uid);
        return result;
    }

    public synchronized boolean isEmpty() {
        return uids.isEmpty() && uidsAsInts.isEmpty();
    }

    public synchronized void clear() {
        uids.clear();
        uidsAsInts.clear();
    }

    public synchronized void addUid(MessageUid uid) {
        addUidUnSynchronized(uid);
    }

    private void addUidUnSynchronized(MessageUid uid) {
        if (usesInts) {
            if (uid.asLong() > INTEGER_MAX_VALUE) {
                switchToLongs();
                addUidUnSynchronized(uid);
                return;
            }
            if (isLastUid(uid)) {
                uidsAsInts.add((int) uid.asLong());
                return;
            }
            if (contains(uid)) {
                return;
            } else {
                uidsAsInts.add((int) uid.asLong());
                uidsAsInts.sort(IntComparators.NATURAL_COMPARATOR);
            }
        } else {
            if (isLastUid(uid)) {
                uids.add(uid.asLong());
                return;
            }
            if (contains(uid)) {
                return;
            } else {
                uids.add(uid.asLong());
                uids.sort(LongComparators.NATURAL_COMPARATOR);
            }
        }
    }

    private boolean contains(MessageUid uid) {
        return getMsnUnsynchronized(uid).foldSilent(() -> false, any -> true);
    }

    private boolean isLastUid(MessageUid uid) {
        Optional<MessageUid> lastUid = getLastUid();
        return lastUid.isEmpty() ||
            lastUid.get().compareTo(uid) < 0;
    }

    private int getLastMsn() {
        return getNumMessage();
    }
}
