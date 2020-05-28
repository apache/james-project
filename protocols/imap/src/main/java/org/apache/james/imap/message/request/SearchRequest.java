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
package org.apache.james.imap.message.request;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.message.request.SearchOperation;

import com.google.common.base.MoreObjects;

public class SearchRequest extends AbstractImapRequest {
    private final SearchOperation operation;
    private final boolean useUids;

    public SearchRequest(SearchOperation operation, boolean useUids, Tag tag) {
        super(tag, ImapConstants.SEARCH_COMMAND);
        this.operation = operation;
        this.useUids = useUids;
    }

    public final SearchOperation getSearchOperation() {
        return operation;
    }

    public final boolean isUseUids() {
        return useUids;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("operation", operation)
            .add("useUids", useUids)
            .toString();
    }
}
