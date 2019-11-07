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
package org.apache.james.mailbox.exception;

import org.apache.james.core.quota.QuotaLimitValue;
import org.apache.james.core.quota.QuotaUsageValue;

/**
 * {@link MailboxException} which identicate that a user was over-quota
 */
public class OverQuotaException extends MailboxException {

    private static final long serialVersionUID = 532673188582481689L;
    
    private QuotaUsageValue<?, ?> used;
    private QuotaLimitValue<?> max;

    public OverQuotaException(String msg, QuotaLimitValue<?> max, QuotaUsageValue<?, ?> used) {
        super(msg);
        this.used = used;
        this.max = max;
    }
    
    public OverQuotaException(QuotaLimitValue<?> max, QuotaUsageValue<?, ?> used) {
        this(null, max, used);
    }
    
    public QuotaUsageValue<?, ?> getUsed() {
        return used;
    }
    
    public QuotaLimitValue<?> getMax() {
        return max;
    }

}
