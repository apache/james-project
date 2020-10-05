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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.NullableMessageSequenceNumber;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

public class UidMsnConverter {

    public static final int FIRST_MSN = 1;

    @VisibleForTesting final ArrayList<MessageUid> uids;

    public UidMsnConverter() {
        this.uids = Lists.newArrayList();
    }

    public synchronized void addAll(List<MessageUid> addedUids) {
        TreeSet<MessageUid> tmp = new TreeSet<>();
        tmp.addAll(uids);
        tmp.addAll(addedUids);
        uids.clear();
        uids.addAll(tmp);
    }

    public synchronized NullableMessageSequenceNumber getMsn(MessageUid uid) {
        int position = Collections.binarySearch(uids, uid);
        if (position < 0) {
            return NullableMessageSequenceNumber.noMessage();
        }
        return NullableMessageSequenceNumber.of(position + 1);
    }

    public synchronized Optional<MessageUid> getUid(int msn) {
        if (msn <= uids.size() && msn > 0) {
            return Optional.of(uids.get(msn - 1));
        }
        return Optional.empty();
    }

    public synchronized Optional<MessageUid> getLastUid() {
        if (uids.isEmpty()) {
            return Optional.empty();
        }
        return getUid(getLastMsn());
    }

    public synchronized Optional<MessageUid> getFirstUid() {
        return getUid(FIRST_MSN);
    }

    public synchronized int getNumMessage() {
        return uids.size();
    }

    public synchronized void remove(MessageUid uid) {
        uids.remove(uid);
    }

    public synchronized boolean isEmpty() {
        return uids.isEmpty();
    }

    public synchronized void clear() {
        uids.clear();
    }

    public synchronized void addUid(MessageUid uid) {
        if (uids.contains(uid)) {
            return;
        }
        if (isLastUid(uid)) {
            uids.add(uid);
        } else {
            uids.add(uid);
            Collections.sort(uids);
        }
    }

    private boolean isLastUid(MessageUid uid) {
        Optional<MessageUid> lastUid = getLastUid();
        return !lastUid.isPresent() ||
            lastUid.get().compareTo(uid) < 0;
    }

    private int getLastMsn() {
        return getNumMessage();
    }
}
