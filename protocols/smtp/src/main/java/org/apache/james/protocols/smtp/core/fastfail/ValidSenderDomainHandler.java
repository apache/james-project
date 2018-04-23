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
package org.apache.james.protocols.smtp.core.fastfail;

import org.apache.james.core.MailAddress;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.dsn.DSNStatus;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.MailHook;

/**
 * Add MFDNSCheck feature to SMTPServer. This handler reject mail from domains which have not an an valid MX record.  
 * 
 */
public abstract class ValidSenderDomainHandler implements MailHook { 


    @Override
    public HookResult doMail(SMTPSession session, MailAddress sender) {
        if (sender != null  && !hasMXRecord(session,sender.getDomain().name())) {
            return new HookResult(HookReturnCode.deny(),
                SMTPRetCode.SYNTAX_ERROR_ARGUMENTS, DSNStatus.getStatus(DSNStatus.PERMANENT,DSNStatus.ADDRESS_SYNTAX_SENDER) + " sender " + sender + " contains a domain with no valid MX records");
        } else {
            return HookResult.DECLINED;
        }
    }
    
    protected abstract boolean hasMXRecord(SMTPSession session, String domain);
}
