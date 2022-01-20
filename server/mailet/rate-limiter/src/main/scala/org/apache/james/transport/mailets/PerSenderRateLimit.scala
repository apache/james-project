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
import eu.timepit.refined.auto._
import javax.inject.Inject
import org.apache.james.core.MailAddress
import org.apache.james.rate.limiter.api.Increment.Increment
import org.apache.james.rate.limiter.api.{AcceptableRate, AllowedQuantity, Increment, RateExceeded, RateLimiter, RateLimiterFactoryProvider, RateLimitingKey, RateLimitingResult, Rule, Rules}
import org.apache.james.util.DurationParser
import org.apache.mailet.Mail
import org.apache.mailet.base.GenericMailet
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.util.Try

case class KeyPrefix(value: String)

sealed trait EntityType {
  def asString(): String

  def extractQuantity(mail: Mail): Option[Increment]
}

case object Count extends EntityType {
  override def asString(): String = "count"

  override def extractQuantity(mail: Mail): Option[Increment] = Some(1)
}
case object RecipientsType extends EntityType {
  override def asString(): String = "recipients"

  override def extractQuantity(mail: Mail): Option[Increment] = Some(Increment.liftOrThrow(mail.getRecipients.size()))
}
case object Size extends EntityType {
  override def asString(): String = "size"

  override def extractQuantity(mail: Mail): Option[Increment] = Some(Increment.liftOrThrow(mail.getMessageSize.toInt))
}
case object TotalSize extends EntityType {
  override def asString(): String = "totalSize"

  override def extractQuantity(mail: Mail): Option[Increment] =
    Try(Math.multiplyExact(mail.getMessageSize.toInt, mail.getRecipients.size()).toInt)
      .map(Increment.liftOrThrow)
      .toOption
}

case class PerSenderRateLimiter(rateLimiter: Option[RateLimiter], keyPrefix: Option[KeyPrefix], entityType: EntityType) {
  def rateLimit(sender: MailAddress, mail: Mail): Publisher[RateLimitingResult] = {
    val rateLimitingKey = SenderKey(keyPrefix, entityType, sender)

    rateLimiter.map(limiter =>
      entityType.extractQuantity(mail)
        .map(increment => limiter.rateLimit(rateLimitingKey, increment))
        .getOrElse(SMono.just[RateLimitingResult](RateExceeded)))
      .getOrElse(SMono.just[RateLimitingResult](AcceptableRate))
  }
}

case class SenderKey(keyPrefix: Option[KeyPrefix], entityType: EntityType, mailAddress: MailAddress) extends RateLimitingKey {
  override def asString(): String = keyPrefix.map(prefix => s"${prefix.value}_${entityType.asString()}_${mailAddress.asString()}")
    .getOrElse(s"${entityType.asString()}_${mailAddress.asString()}")
}

/**
 * <p><b>PerSenderRateLimit</b> allows defining and enforcing rate limits for the sender of matching emails.</p>
 *
 * <ul>This allows writing rules like:
 *   <li>A sender can send 10 emails per hour</li>
 *   <li>A sender can send email to a total of 20 recipients per hour</li>
 *   <li>A sender can send 100 MB of emails (total computed taking only the email size into account) per hour</li>
 *   <li>A sender can send 200 MB of emails (total computed taking each recipient copies into account) per hour</li>
 * </ul>
 *
 * <p>Depending on its position and the matcher it is being combined with, those rate limiting rules could be applied to
 * submitted emails, received emails or emitted email being relayed to third parties.</p>
 *
 *  <ul>Here are supported configuration parameters:
 *    <li><b>keyPrefix</b>: An optional key prefix to apply to rate limiting. Choose distinct values if you specify
 *    this mailet twice within your <code>mailetcontainer.xml</code> file. Defaults to none.</li>
 *    <li><b>exceededProcessor</b>: Processor to which emails whose rate is exceeded should be redirected to. Defaults to error.
 *    Use this to customize the behaviour upon exceeded rate.</li>
 *    <li><b>duration</b>: Duration during which the rate limiting shall be applied. Compulsory, must be a valid duration of at least one second. Supported units includes s (second), m (minute), h (hour), d (day).</li>
 *    <li><b>count</b>: Count of emails allowed for a given sender during duration. Optional, if unspecified this rate limit is not applied.</li>
 *    <li><b>recipients</b>: Count of recipients allowed for a given sender during duration. Optional, if unspecified this rate limit is not applied.</li>
 *    <li><b>size</b>: Size of emails allowed for a given sender during duration (each email count one time, regardless of recipient count). Optional, if unspecified this rate limit is not applied. Supported units : B ( 2^0 ), K ( 2^10 ), M ( 2^20 ), G ( 2^30 ), defaults to B.</li>
 *    <li><b>totalSize</b>: Size of emails allowed for a given sender during duration (each recipient of the email email count one time). Optional, if unspecified this rate limit is not applied. Supported units : B ( 2^0 ), K ( 2^10 ), M ( 2^20 ), G ( 2^30 ), defaults to B. Note that
 *    totalSize is limited in increments of 2exp(31) - ~2 billions: sending a 10MB file to more than 205 recipients will be rejected if this parameter is enabled.</li>
 *  </ul>
 *
 *  <p>For instance, to apply all the examples given above:</p>
 *
 *   <pre><code>
 * &lt;mailet matcher=&quot;All&quot; class=&quot;PerSenderRateLimit&quot;&gt;
 *     &lt;keyPrefix&gt;myPrefix&lt;/keyPrefix&gt;
 *     &lt;duration&gt;1h&lt;/duration&gt;
 *     &lt;count&gt;10&lt;/count&gt;
 *     &lt;recipients&gt;20&lt;/recipients&gt;
 *     &lt;size&gt;100M&lt;/size&gt;
 *     &lt;totalSize&gt;200M&lt;/totalSize&gt;
 * &lt;/mailet&gt;
 *   </code></pre>
 *
 *  <p>Note that to use this extension you need to place the rate-limiter JAR in the <code>extensions-jars</code> folder
 *  and need to configure a viable option to invoke <code>RateLimiterFactoryProvider</code> which can be done by
 *  loading <code>org.apache.james.rate.limiter.memory.MemoryRateLimiterModule</code> Guice module within the
 *  <code>guice.extension.module</code> in <code>extensions.properties</code> configuration file. Note that other Rate
 *  limiter implementation might require extra configuration parameters within your mailet.</p>
 *
 * @param rateLimiterFactoryProvider Allows instantiations of the underlying rate limiters.
 */
class PerSenderRateLimit @Inject()(rateLimiterFactoryProvider: RateLimiterFactoryProvider) extends GenericMailet {
  private var countRateLimiter: PerSenderRateLimiter = _
  private var recipientsRateLimiter: PerSenderRateLimiter = _
  private var sizeRateLimiter: PerSenderRateLimiter = _
  private var totalSizeRateLimiter: PerSenderRateLimiter = _
  private var exceededProcessor: String = _
  private var keyPrefix: Option[KeyPrefix] = _

  override def init(): Unit = {
    val rateLimiterFactory = rateLimiterFactoryProvider.create(getMailetConfig)
    val duration: Duration = parseDuration()

    keyPrefix = Option(getInitParameter("keyPrefix")).map(KeyPrefix)
    exceededProcessor = getInitParameter("exceededProcessor", Mail.ERROR)

    countRateLimiter = PerSenderRateLimiter(rateLimiter = extractCountRules(duration).map(rateLimiterFactory.withSpecification),
      keyPrefix = keyPrefix,
      entityType = Count)
    recipientsRateLimiter = PerSenderRateLimiter(rateLimiter = extractRecipientsRules(duration).map(rateLimiterFactory.withSpecification),
      keyPrefix = keyPrefix,
      entityType = RecipientsType)
    sizeRateLimiter = PerSenderRateLimiter(rateLimiter = extractSizeRules(duration).map(rateLimiterFactory.withSpecification),
      keyPrefix = keyPrefix,
      entityType = Size)
    totalSizeRateLimiter = PerSenderRateLimiter(rateLimiter = extractTotalSizeRules(duration).map(rateLimiterFactory.withSpecification),
      keyPrefix = keyPrefix,
      entityType = TotalSize)
  }

  @VisibleForTesting
  def parseDuration(): Duration =
    Option(getInitParameter("duration"))
      .map(string => DurationParser.parse(string, ChronoUnit.SECONDS))
      .getOrElse(throw new IllegalArgumentException("'duration' is compulsory"))

  private def extractCountRules(duration: Duration): Option[Rules] = Option(getInitParameter("count"))
    .map(_.toLong)
    .map(AllowedQuantity.liftOrThrow)
    .map(quantity => Rules(Seq(Rule(quantity, duration))))

  private def extractRecipientsRules(duration: Duration): Option[Rules] = Option(getInitParameter("recipients"))
    .map(_.toLong)
    .map(AllowedQuantity.liftOrThrow)
    .map(quantity => Rules(Seq(Rule(quantity, duration))))

  private def extractSizeRules(duration: Duration): Option[Rules] = Option(getInitParameter("size"))
    .map {
      case "" => throw new IllegalArgumentException("'size' field cannot be empty if specified")
      case s => s
    }
    .map(org.apache.james.util.Size.parse)
    .map(_.asBytes())
    .map(AllowedQuantity.liftOrThrow)
    .map(quantity => Rules(Seq(Rule(quantity, duration))))

  private def extractTotalSizeRules(duration: Duration): Option[Rules] = Option(getInitParameter("totalSize"))
    .map {
      case "" => throw new IllegalArgumentException("'totalSize' field cannot be empty if specified")
      case s => s
    }
    .map(org.apache.james.util.Size.parse)
    .map(_.asBytes())
    .map(AllowedQuantity.liftOrThrow)
    .map(quantity => Rules(Seq(Rule(quantity, duration))))

  override def service(mail: Mail): Unit = mail.getMaybeSender
      .asOptional()
      .ifPresent(sender => {
        applyRateLimiter(mail, sender)
      })

  private def applyRateLimiter(mail: Mail, sender: MailAddress): Unit = {
    val pivot: RateLimitingResult = AcceptableRate
    val result = SFlux.merge(Seq(
        countRateLimiter.rateLimit(sender, mail),
        recipientsRateLimiter.rateLimit(sender, mail),
        sizeRateLimiter.rateLimit(sender, mail),
        totalSizeRateLimiter.rateLimit(sender, mail)))
      .fold(pivot)((a, b) => a.merge(b))
      .block()

    if (result.equals(RateExceeded)) {
      mail.setState(exceededProcessor)
    }
  }
}
