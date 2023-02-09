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

package org.apache.james.transport.mailets

import java.time.Duration
import java.time.temporal.ChronoUnit
import eu.timepit.refined.auto._
import org.apache.james.rate.limiter.api.Increment.Increment
import org.apache.james.rate.limiter.api.{AllowedQuantity, Increment, Rule, Rules}
import org.apache.james.transport.mailets.ConfigurationOps.{OptionOps, SizeOps}
import org.apache.james.util.DurationParser
import org.apache.mailet.{Mail, MailetConfig}

import scala.util.Try

case class KeyPrefix(value: String)

object ConfigurationOps {

  implicit class OptionOps(mailetConfig: MailetConfig) {
    def getOptionalString(key: String): Option[String] = Option(mailetConfig.getInitParameter(key))
    def getOptionalLong(key: String): Option[Long] = Option(mailetConfig.getInitParameter(key)).map(_.toLong)
  }

  implicit class DurationOps(mailetConfig: MailetConfig) {
    def getDuration(key: String): Option[Duration] = mailetConfig.getOptionalString(key)
      .map(string => DurationParser.parse(string, ChronoUnit.SECONDS))
  }

  implicit class SizeOps(mailetConfig: MailetConfig) {
    def getOptionalSize(key: String): Option[org.apache.james.util.Size] = mailetConfig.getOptionalString(key)
      .map {
        case "" => throw new IllegalArgumentException(s"'$key' field cannot be empty if specified")
        case s => s
      }
      .map(org.apache.james.util.Size.parse)
  }
}


sealed trait EntityType {
  def asString(): String

  def extractQuantity(mail: Mail): Option[Increment]

  def extractRules(duration: Duration, mailetConfig: MailetConfig): Option[Rules]
}

case object Count extends EntityType {
  override def asString(): String = "count"

  override def extractQuantity(mail: Mail): Option[Increment] = Some(1)

  override def extractRules(duration: Duration, mailetConfig: MailetConfig): Option[Rules] = mailetConfig.getOptionalLong("count")
    .map(AllowedQuantity.liftOrThrow)
    .map(quantity => Rules(Seq(Rule(quantity, duration))))
}

case object RecipientsType extends EntityType {
  override def asString(): String = "recipients"

  override def extractQuantity(mail: Mail): Option[Increment] = Some(Increment.liftOrThrow(mail.getRecipients.size()))

  override def extractRules(duration: Duration, mailetConfig: MailetConfig): Option[Rules] = mailetConfig.getOptionalLong("recipients")
    .map(AllowedQuantity.liftOrThrow)
    .map(quantity => Rules(Seq(Rule(quantity, duration))))
}

case object Size extends EntityType {
  override def asString(): String = "size"

  override def extractQuantity(mail: Mail): Option[Increment] = Some(Increment.liftOrThrow(mail.getMessageSize.toInt))

  override def extractRules(duration: Duration, mailetConfig: MailetConfig): Option[Rules] = mailetConfig.getOptionalSize("size")
    .map(_.asBytes())
    .map(AllowedQuantity.liftOrThrow)
    .map(quantity => Rules(Seq(Rule(quantity, duration))))
}

case object TotalSize extends EntityType {
  override def asString(): String = "totalSize"

  override def extractQuantity(mail: Mail): Option[Increment] =
    Try(Math.multiplyExact(mail.getMessageSize.toInt, mail.getRecipients.size()))
      .map(Increment.liftOrThrow)
      .toOption

  override def extractRules(duration: Duration, mailetConfig: MailetConfig): Option[Rules] = mailetConfig.getOptionalSize("totalSize")
    .map(_.asBytes())
    .map(AllowedQuantity.liftOrThrow)
    .map(quantity => Rules(Seq(Rule(quantity, duration))))
}