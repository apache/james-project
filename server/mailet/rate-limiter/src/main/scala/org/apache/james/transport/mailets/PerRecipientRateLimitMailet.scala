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
 ****************************************************************/

package org.apache.james.transport.mailets

import java.time.Duration
import java.time.temporal.ChronoUnit

import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import javax.inject.Inject
import org.apache.james.core.MailAddress
import org.apache.james.lifecycle.api.LifecycleUtil
import org.apache.james.rate.limiter.api.{AcceptableRate, AllowedQuantity, RateExceeded, RateLimiter, RateLimiterFactory, RateLimiterFactoryProvider, RateLimitingKey, RateLimitingResult, Rule, Rules}
import org.apache.james.util.DurationParser
import org.apache.mailet.Mail
import org.apache.mailet.base.GenericMailet
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._
import scala.util.Using;

case class PerRecipientRateLimiter(rateLimiter: RateLimiter, keyPrefix: Option[KeyPrefix], entityType: EntityType) {
  def rateLimit(recipient: MailAddress, mail: Mail): Publisher[RateLimitingResult] =
    entityType.extractQuantity(mail)
      .map(increment => rateLimiter.rateLimit(RecipientKey(keyPrefix, entityType, recipient), increment))
      .getOrElse(SMono.just[RateLimitingResult](RateExceeded))
}

case class RecipientKey(keyPrefix: Option[KeyPrefix], entityType: EntityType, mailAddress: MailAddress) extends RateLimitingKey {
  override def asString(): String = s"${
    keyPrefix.map(prefix => prefix.value + "_")
      .getOrElse("")
  }${entityType.asString()}_${mailAddress.asString()}"
}

class PerRecipientRateLimitMailet @Inject()(rateLimiterFactoryProvider: RateLimiterFactoryProvider) extends GenericMailet {
  private var exceededProcessor: String = _
  private var rateLimiters: Seq[PerRecipientRateLimiter] = _

  override def init(): Unit = {
    val rateLimiterFactory: RateLimiterFactory = rateLimiterFactoryProvider.create(getMailetConfig)
    val duration: Duration = parseDuration()
    val keyPrefix: Option[KeyPrefix] = Option(getInitParameter("keyPrefix")).map(KeyPrefix)
    exceededProcessor = getInitParameter("exceededProcessor", Mail.ERROR)

    val countRateLimiter: Option[PerRecipientRateLimiter] = Option(getInitParameter("count"))
      .flatMap(parameter => extractCountToRateLimiter(parameter, duration, rateLimiterFactory)
        .map(rateLimiter => PerRecipientRateLimiter(rateLimiter, keyPrefix, Count)))

    val sizeRateLimiter: Option[PerRecipientRateLimiter] = Option(getInitParameter("size"))
      .map {
        case "" => throw new IllegalArgumentException("'size' field cannot be empty if specified")
        case s => s
      }
      .flatMap(parameter => extractSizeToRateLimiter(parameter, duration, rateLimiterFactory)
        .map(rateLimiter => PerRecipientRateLimiter(rateLimiter, keyPrefix, Size)))

    rateLimiters = Seq(countRateLimiter, sizeRateLimiter)
      .filter(limiter => limiter.isDefined)
      .map(limiter => limiter.get)
  }

  override def service(mail: Mail): Unit = {
    if (!mail.getRecipients.isEmpty) {
      val rateLimitResults: Seq[(MailAddress, RateLimitingResult)] = mail.getRecipients.asScala.toSeq
        .map(recipient => (recipient, applyRateLimiter(mail, recipient)))

      val rateLimitedRecipients: Seq[MailAddress] = rateLimitResults.filter(_._2.equals(RateExceeded)).map(_._1)
      val acceptableRecipients: Seq[MailAddress] = rateLimitResults.filter(_._2.equals(AcceptableRate)).map(_._1)

      (acceptableRecipients, rateLimitedRecipients) match {
        case (acceptable, _) if acceptable.isEmpty => mail.setState(exceededProcessor)
        case (_, exceeded) if exceeded.isEmpty => // do nothing
        case _ =>
          mail.setRecipients(ImmutableList.copyOf(acceptableRecipients.asJava))

          Using(mail.duplicate())(newMail => {
            newMail.setRecipients(ImmutableList.copyOf(rateLimitedRecipients.asJava))
            getMailetContext.sendMail(newMail, exceededProcessor)
          })(LifecycleUtil.dispose(_))
      }
    }
  }

  @VisibleForTesting
  def parseDuration(): Duration =
    Option(getInitParameter("duration"))
      .map(string => DurationParser.parse(string, ChronoUnit.SECONDS))
      .getOrElse(throw new IllegalArgumentException("'duration' is compulsory"))


  private def extractCountToRateLimiter(quantity: String, duration: Duration, rateLimiterFactory: RateLimiterFactory): Option[RateLimiter] =
    Some(quantity)
      .map(_.toLong)
      .map(AllowedQuantity.liftOrThrow)
      .map(quantity => Rules(Seq(Rule(quantity, duration))))
      .map(rateLimiterFactory.withSpecification)

  private def extractSizeToRateLimiter(quantity: String, duration: Duration, rateLimiterFactory: RateLimiterFactory): Option[RateLimiter] =
    Some(quantity)
      .map(org.apache.james.util.Size.parse)
      .map(_.asBytes())
      .map(AllowedQuantity.liftOrThrow)
      .map(quantity => Rules(Seq(Rule(quantity, duration))))
      .map(rateLimiterFactory.withSpecification)

  private def applyRateLimiter(mail: Mail, recipient: MailAddress): RateLimitingResult =
    SFlux.merge(rateLimiters.map(rateLimiter => rateLimiter.rateLimit(recipient, mail)))
      .fold[RateLimitingResult](AcceptableRate)((a, b) => a.merge(b))
      .block()

}
