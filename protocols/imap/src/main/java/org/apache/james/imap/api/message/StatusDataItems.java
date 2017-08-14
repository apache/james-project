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

import com.google.common.base.MoreObjects;

public class StatusDataItems {
    private boolean messages;

    private boolean recent;

    private boolean uidNext;

    private boolean uidValidity;

    private boolean unseen;

    private boolean highestModSeq;

    public boolean isMessages() {
        return messages;
    }

    public void setMessages(boolean messages) {
        this.messages = messages;
    }

    public boolean isRecent() {
        return recent;
    }

    public void setRecent(boolean recent) {
        this.recent = recent;
    }

    public boolean isUidNext() {
        return uidNext;
    }

    public void setUidNext(boolean uidNext) {
        this.uidNext = uidNext;
    }

    public boolean isUidValidity() {
        return uidValidity;
    }

    public void setUidValidity(boolean uidValidity) {
        this.uidValidity = uidValidity;
    }

    public boolean isUnseen() {
        return unseen;
    }

    public void setUnseen(boolean unseen) {
        this.unseen = unseen;
    }

    public void setHighestModSeq(boolean highestModSeq) {
        this.highestModSeq = highestModSeq;
    }
    
    public boolean isHighestModSeq() {
        return highestModSeq;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("messages", messages)
            .add("recent", recent)
            .add("uidNext", uidNext)
            .add("uidValidity", uidValidity)
            .add("unseen", unseen)
            .add("highestModSeq", highestModSeq)
            .toString();
    }
}
