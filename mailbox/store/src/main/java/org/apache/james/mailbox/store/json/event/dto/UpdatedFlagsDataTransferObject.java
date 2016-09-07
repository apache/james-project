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

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.model.UpdatedFlags;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UpdatedFlagsDataTransferObject {
    @JsonProperty("uid")
    private long uid;
    @JsonProperty("modseq")
    private long modseq;
    @JsonProperty("oldFlags")
    private FlagsDataTransferObject oldFlags;
    @JsonProperty("newFlags")
    private FlagsDataTransferObject newFlags;

    public UpdatedFlagsDataTransferObject() {
    }

    public UpdatedFlagsDataTransferObject(UpdatedFlags updatedFlags) {
        this.uid = updatedFlags.getUid().asLong();
        this.modseq = updatedFlags.getModSeq();
        this.oldFlags = new FlagsDataTransferObject(updatedFlags.getOldFlags());
        this.newFlags = new FlagsDataTransferObject(updatedFlags.getNewFlags());
    }

    public UpdatedFlags retrieveUpdatedFlags() {
        return new UpdatedFlags(MessageUid.of(uid), modseq, oldFlags.getFlags(), newFlags.getFlags());
    }

}