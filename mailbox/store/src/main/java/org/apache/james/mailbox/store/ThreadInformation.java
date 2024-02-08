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

package org.apache.james.mailbox.store;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.james.mailbox.store.mail.model.MimeMessageId;
import org.apache.james.mailbox.store.mail.model.Subject;
import org.apache.james.mailbox.store.mail.utils.MimeMessageHeadersUtil;
import org.apache.james.mailbox.store.search.SearchUtil;
import org.apache.james.mime4j.message.HeaderImpl;

import com.google.common.hash.Hashing;

public class ThreadInformation {
    public static class Hashed {
        private final Set<Integer> hashMimeMessageIds;
        private final Optional<Integer> hashBaseSubject;

        public Hashed(Set<Integer> hashMimeMessageIds, Optional<Integer> hashBaseSubject) {
            this.hashMimeMessageIds = hashMimeMessageIds;
            this.hashBaseSubject = hashBaseSubject;
        }

        public Set<Integer> getHashMimeMessageIds() {
            return hashMimeMessageIds;
        }

        public Optional<Integer> getHashBaseSubject() {
            return hashBaseSubject;
        }
    }

    public static ThreadInformation of(HeaderImpl headers) {
        Optional<MimeMessageId> mimeMessageId = MimeMessageHeadersUtil.parseMimeMessageId(headers);
        Optional<MimeMessageId> inReplyTo = MimeMessageHeadersUtil.parseInReplyTo(headers);
        Optional<List<MimeMessageId>> references = MimeMessageHeadersUtil.parseReferences(headers);
        Optional<Subject> subject = MimeMessageHeadersUtil.parseSubject(headers);

        return new ThreadInformation(mimeMessageId, inReplyTo, references, subject);
    }

    private final Optional<MimeMessageId> mimeMessageId;
    private final Optional<MimeMessageId> inReplyTo;
    private final Optional<List<MimeMessageId>> references;
    private final Optional<Subject> subject;

    public ThreadInformation(Optional<MimeMessageId> mimeMessageId, Optional<MimeMessageId> inReplyTo, Optional<List<MimeMessageId>> references, Optional<Subject> subject) {
        this.mimeMessageId = mimeMessageId;
        this.inReplyTo = inReplyTo;
        this.references = references;
        this.subject = subject;
    }

    public Hashed hash() {
        Set<Integer> hashMimeMessageIds = buildMimeMessageIdSet()
            .stream()
            .map(mimeMessageId1 -> Hashing.murmur3_32_fixed().hashBytes(mimeMessageId1.getValue().getBytes()).asInt())
            .collect(Collectors.toSet());

        Optional<Integer> hashBaseSubject = subject.map(value -> new Subject(SearchUtil.getBaseSubject(value.getValue())))
            .map(subject1 -> Hashing.murmur3_32_fixed().hashBytes(subject1.getValue().getBytes()).asInt());

        return new Hashed(hashMimeMessageIds, hashBaseSubject);
    }

    private Set<MimeMessageId> buildMimeMessageIdSet() {
        Set<MimeMessageId> mimeMessageIds = new HashSet<>();
        mimeMessageId.ifPresent(mimeMessageIds::add);
        inReplyTo.ifPresent(mimeMessageIds::add);
        references.ifPresent(mimeMessageIds::addAll);
        return mimeMessageIds;
    }

    public Optional<MimeMessageId> getMimeMessageId() {
        return mimeMessageId;
    }

    public Optional<MimeMessageId> getInReplyTo() {
        return inReplyTo;
    }

    public Optional<List<MimeMessageId>> getReferences() {
        return references;
    }

    public Optional<Subject> getSubject() {
        return subject;
    }
}
