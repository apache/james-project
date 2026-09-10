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

package org.apache.james.jmap.rfc8621.contract.custom.emailsubmission

import eu.timepit.refined.auto._
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.{SetErrorDescription, SetErrorType}
import org.apache.james.jmap.method.EmailSubmissionSetValidation
import org.apache.mailet.Mail
import reactor.core.scala.publisher.SMono

object RejectAllEmailSubmissionSetValidation {
  val REJECTED: SetErrorType = "customRejection"
  val DESCRIPTION: String = "Rejected by the custom validation"
}

case class RejectAllEmailSubmissionSetValidation() extends EmailSubmissionSetValidation {
  override def validate(mail: Mail): SMono[Option[SetError]] =
    SMono.just(Some(SetError(RejectAllEmailSubmissionSetValidation.REJECTED,
      SetErrorDescription(RejectAllEmailSubmissionSetValidation.DESCRIPTION), None)))
}
