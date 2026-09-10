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

package org.apache.james.jmap.method

import org.apache.james.jmap.core.SetError
import org.apache.mailet.Mail
import reactor.core.scala.publisher.SMono

/**
 * Extension point allowing to reject an EmailSubmission/set creation before the mail gets spooled,
 * the JMAP counterpart of the SMTP `RcptHook` / `MailHook` mechanism.
 *
 * Returning a `SetError` turns the creation into a `notCreated` entry: the client is told
 * synchronously rather than through an asynchronous bounce.
 *
 * Implementations are expected to emit `None` when the mail is accepted, and `Some(setError)` 
 * when it is rejected. 
 */
trait EmailSubmissionSetValidation {
  def validate(mail: Mail): SMono[Option[SetError]]
}
