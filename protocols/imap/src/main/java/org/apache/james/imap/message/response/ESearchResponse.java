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

package org.apache.james.imap.message.response;

import java.util.List;

import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.request.SearchResultOption;
import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.mailbox.ModSeq;

public class ESearchResponse implements ImapResponseMessage {
    private final long minUid;
    private final long maxUid;
    private final long count;
    private final IdRange[] all;
    private final Tag tag;
    private final boolean useUid;
    private final List<SearchResultOption> options;
    private final ModSeq highestModSeq;

    public ESearchResponse(long minUid, long maxUid, long count, IdRange[] all, ModSeq highestModSeq, Tag tag, boolean useUid, List<SearchResultOption> options) {
        this.options = options;
        this.minUid = minUid;
        this.maxUid = maxUid;
        this.count = count;
        this.all = all;
        this.tag = tag;
        this.useUid = useUid;
        this.highestModSeq = highestModSeq;
    }
    
    public final long getCount() {
        return count;
    }
    
    public final long getMinUid() {
        return minUid;
    }
    
    public final long getMaxUid() {
        return maxUid;
    }
    
    public IdRange[] getAll() {
        return all;
    }
    
    public Tag getTag() {
        return tag;
    }
    
    public boolean getUseUid() {
        return useUid;
    }
    
    public List<SearchResultOption> getSearchResultOptions() {
        return options;
    }
    
    public final ModSeq getHighestModSeq() {
        return highestModSeq;
    }
    
}
