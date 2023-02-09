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
import java.util
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList

import javax.inject.Inject
import org.apache.james.core.MailAddress
import org.apache.james.lifecycle.api.LifecycleUtil
import org.apache.james.rate.limiter.api.{AcceptableRate, RateExceeded, RateLimiter, RateLimiterFactory, RateLimitingKey, RateLimitingResult}
import org.apache.james.transport.mailets.ConfigurationOps.{DurationOps, OptionOps}
import org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY
import org.apache.mailet.base.GenericMailet
import org.apache.mailet.{Mail, ProcessingState}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._
import scala.util.Using

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

/**
 * <p><b>PerRecipientRateLimit</b> allows defining and enforcing rate limits for the recipients of matching emails.</p>
 *
 * <ul>This allows writing rules like:
 *   <li>A recipient can receive 10 emails per hour</li>
 *   <li>A recipient can receive 100 MB of emails per hour</li>
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
 *    <li><b>size</b>: Size of emails allowed for a given sender during duration (each email count one time, regardless of recipient count). Optional, if unspecified this rate limit is not applied. Supported units : B ( 2^0 ), K ( 2^10 ), M ( 2^20 ), G ( 2^30 ), defaults to B.</li>
 *  </ul>
 *
 *  <p>For instance, to apply all the examples given above:</p>
 *
 *   <pre><code>
 * &lt;mailet matcher=&quot;All&quot; class=&quot;PerRecipientRateLimit&quot;&gt;
 *     &lt;keyPrefix&gt;myPrefix&lt;/keyPrefix&gt;
 *     &lt;duration&gt;1h&lt;/duration&gt;
 *     &lt;count&gt;10&lt;/count&gt;
 *     &lt;size&gt;100M&lt;/size&gt;
 *     &lt;exceededProcessor&gt;tooMuchMails&lt;/exceededProcessor&gt;
 * &lt;/mailet&gt;
 *   </code></pre>
 *
 *  <p>Note that to use this extension you need to place the rate-limiter JAR in the <code>extensions-jars</code> folder
 *  and need to configure a viable option to invoke <code>RateLimiterFactory</code> which can be done by
 *  loading <code>org.apache.james.rate.limiter.memory.MemoryRateLimiterModule</code> Guice module within the
 *  <code>guice.extension.module</code> in <code>extensions.properties</code> configuration file. Note that other Rate
 *  limiter implementation might require extra configuration parameters within your mailet.</p>
 *
 * @param rateLimiterFactory Allows instantiations of the underlying rate limiters.
 */
class PerRecipientRateLimit @Inject()(rateLimiterFactory: RateLimiterFactory) extends GenericMailet {
  private var exceededProcessor: String = _
  private var rateLimiters: Seq[PerRecipientRateLimiter] = _

  override def init(): Unit = {
    val duration: Duration = parseDuration()
    val precision: Option[Duration] = getMailetConfig.getDuration("precision")
    val keyPrefix: Option[KeyPrefix] = getMailetConfig.getOptionalString("keyPrefix").map(KeyPrefix)
    exceededProcessor = getMailetConfig.getOptionalString("exceededProcessor").getOrElse(Mail.ERROR)

    def perRecipientRateLimiter(entityType: EntityType): Option[PerRecipientRateLimiter] = createRateLimiter(entityType, duration, precision, rateLimiterFactory, keyPrefix)

    rateLimiters = Seq(Size, Count).flatMap(perRecipientRateLimiter)
  }

  override def service(mail: Mail): Unit = {
    if (!mail.getRecipients.isEmpty) {
      val rateLimitResults: Seq[(MailAddress, RateLimitingResult)] = applyRateLimiter(mail)

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
  def parseDuration(): Duration = getMailetConfig.getDuration("duration")
    .getOrElse(throw new IllegalArgumentException("'duration' is compulsory"))

  private def createRateLimiter(entityType: EntityType, duration: Duration, precision: Option[Duration],
                                rateLimiterFactory: RateLimiterFactory, keyPrefix: Option[KeyPrefix]): Option[PerRecipientRateLimiter] =
    entityType.extractRules(duration, getMailetConfig)
      .map(rateLimiterFactory.withSpecification(_, precision))
      .map(PerRecipientRateLimiter(_, keyPrefix, entityType))


  private def applyRateLimiter(mail: Mail): Seq[(MailAddress, RateLimitingResult)] =
    SFlux.fromIterable(mail.getRecipients.asScala)
      .flatMap(recipient => SFlux.merge(rateLimiters.map(rateLimiter => rateLimiter.rateLimit(recipient, mail)))
        .fold[RateLimitingResult](AcceptableRate)((a, b) => a.merge(b))
        .map(rateLimitingResult => (recipient, rateLimitingResult)), DEFAULT_CONCURRENCY)
      .collectSeq()
      .block()


  override def requiredProcessingState(): util.Collection[ProcessingState] = ImmutableList.of(new ProcessingState(exceededProcessor))

}
