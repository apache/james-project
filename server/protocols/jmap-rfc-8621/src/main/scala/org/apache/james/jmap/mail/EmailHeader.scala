/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/

package org.apache.james.jmap.mail

import java.nio.charset.StandardCharsets.US_ASCII

import org.apache.james.mime4j.stream.Field

object EmailHeader {
  def apply(field: Field): EmailHeader = EmailHeader(EmailHeaderName(field.getName), EmailHeaderValue(field.getBody))
}

object EmailHeaderValue {
  def from(field: Field): EmailHeaderValue = EmailHeaderValue(new String(field.getRaw.toByteArray, US_ASCII).substring(field.getName.length + 1))
}

case class EmailHeaderName(value: String) extends AnyVal
case class EmailHeaderValue(value: String) extends AnyVal

case class EmailHeader(name: EmailHeaderName, value: EmailHeaderValue)
