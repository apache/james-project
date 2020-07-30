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

import java.util.Optional;
import java.util.function.Function;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.NullableMessageSequenceNumber;

import com.google.common.annotations.VisibleForTesting;

import io.vavr.collection.TreeSet;

public class UidMsnConverter {

    @VisibleForTesting TreeSet<MessageUid> uids;

    public UidMsnConverter() {
        this.uids = TreeSet.empty();
    }

    public synchronized void addAll(java.util.List<MessageUid> addedUids) {
        uids = uids.addAll(addedUids);
    }

    public NullableMessageSequenceNumber getMsn(MessageUid uid) {
        return uids
            .zipWithIndex()
            .toMap(Function.identity())
            .get(uid)
            .map(position -> NullableMessageSequenceNumber.of(position + 1))
            .getOrElse(NullableMessageSequenceNumber.noMessage());
    }

    public Optional<MessageUid> getUid(int msn) {
        return uids
            .drop(msn - 1)
            .headOption()
            .toJavaOptional();
    }

    public Optional<MessageUid> getLastUid() {
        return uids.lastOption().toJavaOptional();
    }

    public Optional<MessageUid> getFirstUid() {
        return uids.headOption().toJavaOptional();
    }

    public int getNumMessage() {
        return uids.size();
    }

    public synchronized void remove(MessageUid uid) {
        uids = uids.remove(uid);
    }

    public boolean isEmpty() {
        return uids.isEmpty();
    }

    public synchronized void clear() {
        uids = TreeSet.empty();
    }

    public synchronized void addUid(MessageUid uid) {
        uids = uids.add(uid);
    }

}
