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

import java.time.Duration

import eu.timepit.refined.auto._
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Properties, SetError}
import org.apache.mailet.Mail
import reactor.core.scala.publisher.SMono

object RejectDelayEmailSubmissionSetValidation {
  val DESCRIPTION: String = "The mail queue of this server does not support delayed sends"
}

/**
 * Rejects delayed sends (`holdFor` / `holdUntil`) for servers whose mail queue would silently
 * drop the delay, and thus send the mail right away while `sendAt` announces a later date.
 */
class RejectDelayEmailSubmissionSetValidation extends EmailSubmissionSetValidation {
  override def validate(mail: Mail): SMono[Option[SetError]] = SMono.just(None)

  override def validate(mail: Mail, delay: Duration): SMono[Option[SetError]] =
    if (delay.isNegative || delay.isZero) {
      SMono.just(None)
    } else {
      SMono.just(Some(SetError.invalidProperties(
        SetErrorDescription(RejectDelayEmailSubmissionSetValidation.DESCRIPTION),
        Some(Properties("envelope")))))
    }
}
