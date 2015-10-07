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

package org.apache.james.adapter.mailbox;

import org.apache.james.mailbox.model.Quota;

import java.io.Serializable;

public class SerializableQuota implements Serializable {

    private final long max;
    private final long used;

    public SerializableQuota(long max, long used) {
        this.max = max;
        this.used = used;
    }

    public SerializableQuota(Quota quota) {
        this.max = quota.getMax();
        this.used = quota.getUsed();
    }

    public long getMax() {
        return max;
    }

    public long getUsed() {
        return used;
    }

}
