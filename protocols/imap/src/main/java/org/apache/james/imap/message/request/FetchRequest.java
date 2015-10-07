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

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.message.FetchData;
import org.apache.james.imap.api.message.IdRange;

public class FetchRequest extends AbstractImapRequest {
    private final boolean useUids;

    private final IdRange[] idSet;

    private final FetchData fetch;

    public FetchRequest(final ImapCommand command, final boolean useUids, final IdRange[] idSet, final FetchData fetch, String tag) {
        super(tag, command);
        this.useUids = useUids;
        this.idSet = idSet;
        this.fetch = fetch;
        if (useUids) {
            fetch.setUid(true);
        }
    }

    public final FetchData getFetch() {
        return fetch;
    }

    public final IdRange[] getIdSet() {
        return idSet;
    }

    public final boolean isUseUids() {
        return useUids;
    }

}
