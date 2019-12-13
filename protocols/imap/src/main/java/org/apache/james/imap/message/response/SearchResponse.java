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

import java.util.Arrays;
import java.util.Objects;

import org.apache.james.imap.api.message.response.ImapResponseMessage;
import org.apache.james.mailbox.ModSeq;

/**
 * A <code>SEARCH</code> response.
 */
public class SearchResponse implements ImapResponseMessage {
    private final long[] ids;
    private final ModSeq highestModSeq;

    /**
     * Constructs a <code>SEARCH</code> response.
     * 
     * @param ids ids, not null
     */
    public SearchResponse(long[] ids, ModSeq highestModSeq) {
        this.ids = ids;
        this.highestModSeq = highestModSeq;
    }

    /**
     * Gets the ids returned by this search.
     * 
     * @return the ids, not null
     */
    public final long[] getIds() {
        return ids;
    }
    
    /**
     * Return the highest mod-sequence for which matched the search. This is only set if the search included the MODSEQ 
     * parameter
     *  
     * @return highestMod
     */
    public final ModSeq getHighestModSeq() {
        return highestModSeq;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof SearchResponse) {
            SearchResponse that = (SearchResponse) o;

            return Arrays.equals(this.ids, that.ids)
                && Objects.equals(this.highestModSeq, that.highestModSeq);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(Arrays.hashCode(ids), highestModSeq);
    }

    /**
     * Constructs a <code>String</code> with all attributes in name = value
     * format.
     * 
     * @return a <code>String</code> representation of this object.
     */
    public String toString() {
        final String TAB = " ";

        StringBuilder retValue = new StringBuilder();

        retValue.append("SearchResponse ( ").append("ids = ").append(Arrays.toString(this.ids)).append(TAB).append(" )");

        return retValue.toString();
    }
}
