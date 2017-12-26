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

package org.apache.james.mailbox.store.json.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.mail.Flags;

public class FlagsDataTransferObject {
    @JsonProperty()
    private boolean answered;
    @JsonProperty()
    private boolean flagged;
    @JsonProperty()
    private boolean recent;
    @JsonProperty()
    private boolean deleted;
    @JsonProperty()
    private boolean draft;
    @JsonProperty()
    private boolean seen;
    @JsonProperty()
    private String[] userFlags;

    public FlagsDataTransferObject() {

    }

    public FlagsDataTransferObject(Flags flags) {
        this.answered = flags.contains(Flags.Flag.ANSWERED);
        this.flagged = flags.contains(Flags.Flag.FLAGGED);
        this.recent = flags.contains(Flags.Flag.RECENT);
        this.deleted = flags.contains(Flags.Flag.DELETED);
        this.draft = flags.contains(Flags.Flag.DRAFT);
        this.seen = flags.contains(Flags.Flag.SEEN);
        this.userFlags = flags.getUserFlags();
    }

    @JsonIgnore
    public Flags getFlags() {
        Flags result = new Flags();
        if (answered) {
            result.add(Flags.Flag.ANSWERED);
        }
        if (flagged) {
            result.add(Flags.Flag.FLAGGED);
        }
        if (recent) {
            result.add(Flags.Flag.RECENT);
        }
        if (deleted) {
            result.add(Flags.Flag.DELETED);
        }
        if (draft) {
            result.add(Flags.Flag.DRAFT);
        }
        if (seen) {
            result.add(Flags.Flag.SEEN);
        }
        for (String flag : userFlags) {
            result.add(flag);
        }
        return result;
    }
}