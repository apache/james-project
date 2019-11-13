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

import java.util.EnumSet;

import com.google.common.base.MoreObjects;

public class StatusDataItems {
    public enum StatusItem {
        MESSAGES,
        RECENT,
        UID_NEXT,
        UID_VALIDITY,
        UNSEEN,
        HIGHEST_MODSEQ
    }

    private final EnumSet<StatusItem> statusItems;

    public StatusDataItems(EnumSet<StatusItem> statusItems) {
        this.statusItems = statusItems;
    }

    public boolean isMessages() {
        return statusItems.contains(StatusItem.MESSAGES);
    }

    public boolean isRecent() {
        return statusItems.contains(StatusItem.RECENT);
    }

    public boolean isUidNext() {
        return statusItems.contains(StatusItem.UID_NEXT);
    }

    public boolean isUidValidity() {
        return statusItems.contains(StatusItem.UID_VALIDITY);
    }

    public boolean isUnseen() {
        return statusItems.contains(StatusItem.UNSEEN);
    }
    
    public boolean isHighestModSeq() {
        return statusItems.contains(StatusItem.HIGHEST_MODSEQ);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("messages", isMessages())
            .add("recent", isRecent())
            .add("uidNext", isUidNext())
            .add("uidValidity", isUidValidity())
            .add("unseen", isUnseen())
            .add("highestModSeq", isHighestModSeq())
            .toString();
    }
}
