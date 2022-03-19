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
package org.apache.james.mailbox.cassandra.table;

import jakarta.mail.Flags;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.google.common.collect.ImmutableMap;

public interface Flag {
    CqlIdentifier ANSWERED = CqlIdentifier.fromCql("flagAnswered");
    CqlIdentifier DELETED = CqlIdentifier.fromCql("flagDeleted");
    CqlIdentifier DRAFT = CqlIdentifier.fromCql("flagDraft");
    CqlIdentifier RECENT = CqlIdentifier.fromCql("flagRecent");
    CqlIdentifier SEEN = CqlIdentifier.fromCql("flagSeen");
    CqlIdentifier FLAGGED = CqlIdentifier.fromCql("flagFlagged");
    CqlIdentifier USER = CqlIdentifier.fromCql("flagUser");
    CqlIdentifier USER_FLAGS = CqlIdentifier.fromCql("userFlags");

    CqlIdentifier[] ALL_LOWERCASE = {
        ANSWERED,
        DELETED,
        DRAFT,
        RECENT,
        SEEN,
        FLAGGED,
        USER
    };

    ImmutableMap<CqlIdentifier, Flags.Flag> JAVAX_MAIL_FLAG = ImmutableMap.<CqlIdentifier, Flags.Flag>builder()
        .put(ANSWERED, Flags.Flag.ANSWERED)
        .put(DELETED, Flags.Flag.DELETED)
        .put(DRAFT, Flags.Flag.DRAFT)
        .put(RECENT, Flags.Flag.RECENT)
        .put(SEEN, Flags.Flag.SEEN)
        .put(FLAGGED, Flags.Flag.FLAGGED)
        .put(USER, Flags.Flag.USER)
        .build();
}