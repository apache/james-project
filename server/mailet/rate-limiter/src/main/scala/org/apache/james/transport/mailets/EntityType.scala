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
import org.apache.james.util.DurationParser
import org.apache.mailet.{Mail, MailetConfig}

import scala.util.Try

case class KeyPrefix(value: String)

object DurationParsingUtil {
  def parseDuration(mailetConfig: MailetConfig): Duration = Option(mailetConfig.getInitParameter("duration"))
    .map(string => DurationParser.parse(string, ChronoUnit.SECONDS))
    .getOrElse(throw new IllegalArgumentException("'duration' is compulsory"))
}

object PrecisionParsingUtil {
  def parsePrecision(mailetConfig: MailetConfig): Option[Duration] = Option(mailetConfig.getInitParameter("precision"))
    .map(string => DurationParser.parse(string, ChronoUnit.SECONDS))
}

sealed trait EntityType {
  def asString(): String

  def extractQuantity(mail: Mail): Option[Increment]

  def extractRules(duration: Duration, mailetConfig: MailetConfig): Option[Rules]
}

case object Count extends EntityType {
  override def asString(): String = "count"

  override def extractQuantity(mail: Mail): Option[Increment] = Some(1)

  override def extractRules(duration: Duration, mailetConfig: MailetConfig): Option[Rules] = Option(mailetConfig.getInitParameter("count"))
    .map(_.toLong)
    .map(AllowedQuantity.liftOrThrow)
    .map(quantity => Rules(Seq(Rule(quantity, duration))))
}

case object RecipientsType extends EntityType {
  override def asString(): String = "recipients"

  override def extractQuantity(mail: Mail): Option[Increment] = Some(Increment.liftOrThrow(mail.getRecipients.size()))

  override def extractRules(duration: Duration, mailetConfig: MailetConfig): Option[Rules] = Option(mailetConfig.getInitParameter("recipients"))
    .map(_.toLong)
    .map(AllowedQuantity.liftOrThrow)
    .map(quantity => Rules(Seq(Rule(quantity, duration))))
}

case object Size extends EntityType {
  override def asString(): String = "size"

  override def extractQuantity(mail: Mail): Option[Increment] = Some(Increment.liftOrThrow(mail.getMessageSize.toInt))

  override def extractRules(duration: Duration, mailetConfig: MailetConfig): Option[Rules] = Option(mailetConfig.getInitParameter("size"))
    .map {
      case "" => throw new IllegalArgumentException("'size' field cannot be empty if specified")
      case s => s
    }
    .map(org.apache.james.util.Size.parse)
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

  override def extractRules(duration: Duration, mailetConfig: MailetConfig): Option[Rules] = Option(mailetConfig.getInitParameter("totalSize"))
    .map {
      case "" => throw new IllegalArgumentException("'totalSize' field cannot be empty if specified")
      case s => s
    }
    .map(org.apache.james.util.Size.parse)
    .map(_.asBytes())
    .map(AllowedQuantity.liftOrThrow)
    .map(quantity => Rules(Seq(Rule(quantity, duration))))
}