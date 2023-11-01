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

import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.decode.DecodingException;

import it.unimi.dsi.fastutil.longs.LongList;

public class PartialRange {
    private final long lowVal;
    private final long upVal;

    public PartialRange(long lowVal, long upVal) throws DecodingException {
        checkArgument(lowVal != 0);
        checkArgument(upVal != 0);
        checkArgument(lowVal > 0 == upVal > 0);
        checkArgument(Math.abs(lowVal) <= Math.abs(upVal));
        this.lowVal = lowVal;
        this.upVal = upVal;
    }

    public void checkArgument(boolean condition) throws DecodingException {
        if (!condition) {
            throw new DecodingException(HumanReadableText.ILLEGAL_ARGUMENTS, "Invalid partial range");
        }
    }

    public long getLowVal() {
        return lowVal;
    }

    public long getUpVal() {
        return upVal;
    }

    public LongList filter(LongList uids) {
        if (lowVal > 0) {
            int from = stanitize((int) lowVal - 1, uids);
            int to = stanitize((int) upVal, uids);
            return uids.subList(from, to);
        }
        int from = stanitize(uids.size() + (int) upVal, uids);
        int to = stanitize(uids.size() + (int) lowVal + 1, uids);
        return uids.subList(from, to);
    }

    public int stanitize(int i, LongList longs) {
        if (i < 0) {
            return 0;
        }
        if (i > longs.size()) {
            return longs.size();
        }
        return i;
    }
}
