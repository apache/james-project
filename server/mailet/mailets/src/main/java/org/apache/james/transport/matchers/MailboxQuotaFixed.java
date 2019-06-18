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
package org.apache.james.transport.matchers;

import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Experimental;
import org.apache.mailet.Mail;
import org.apache.mailet.Matcher;

/**
 * Matcher which can be used to set a quota for users Mailbox. This
 * {@link Matcher} need to recalculate the used space of users mailbox on every
 * call. So use it with caution!
 *
 * @deprecated JAMES-2703 This class is deprecated and will be removed straight after upcoming James 3.4.0 release
 *
 * Please use IsOverQuota which relies on mailbox quota apis and avoids scanning
 */
@Experimental
@Deprecated
public class MailboxQuotaFixed extends AbstractStorageQuota {

    @Override
    protected long getQuota(MailAddress arg0, Mail arg1) throws MessagingException {
        return parseQuota(this.getCondition());
    }

}
