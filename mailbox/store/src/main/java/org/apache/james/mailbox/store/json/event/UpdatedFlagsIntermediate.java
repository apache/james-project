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

package org.apache.james.mailbox.store.json.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.james.mailbox.model.UpdatedFlags;

public class UpdatedFlagsIntermediate {
    @JsonProperty("h")
    public long uid;
    @JsonProperty("i")
    public long modseq;
    @JsonProperty("j")
    public FlagsIntermediate oldFlags;
    @JsonProperty("k")
    public FlagsIntermediate newFlags;

    public UpdatedFlagsIntermediate() {

    }

    protected UpdatedFlagsIntermediate(UpdatedFlags updatedFlags) {
        this.uid = updatedFlags.getUid();
        this.modseq = updatedFlags.getModSeq();
        this.oldFlags = new FlagsIntermediate(updatedFlags.getOldFlags());
        this.newFlags = new FlagsIntermediate(updatedFlags.getNewFlags());
    }

    protected UpdatedFlags getUpdatedFlags() {
        return new UpdatedFlags(uid, modseq, oldFlags.getFlags(), newFlags.getFlags());
    }

}