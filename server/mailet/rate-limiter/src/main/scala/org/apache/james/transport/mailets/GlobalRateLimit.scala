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
import java.util
import com.google.common.collect.ImmutableList

import javax.inject.Inject
import org.apache.james.rate.limiter.api.{AcceptableRate, RateExceeded, RateLimiter, RateLimiterFactory, RateLimitingKey, RateLimitingResult}
import org.apache.mailet.base.GenericMailet
import org.apache.mailet.{Mail, ProcessingState}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

case class GlobalKey(keyPrefix: Option[KeyPrefix], entityType: EntityType) extends RateLimitingKey {
  override val asString: String = {
    val key = s"${entityType.asString}_global}"
    keyPrefix.map(prefix => s"${prefix.value}_$key").getOrElse(key)
  }
}

class GlobalRateLimiter(rateLimiter: Option[RateLimiter], keyPrefix: Option[KeyPrefix], entityType: EntityType) {
  def rateLimit(mail: Mail): Publisher[RateLimitingResult] = {
    val rateLimitingKey = GlobalKey(keyPrefix, entityType)

    rateLimiter.map(limiter =>
      EntityType.extractQuantity(entityType, mail)
        .map(increment => limiter.rateLimit(rateLimitingKey, increment))
        .getOrElse(SMono.just[RateLimitingResult](RateExceeded)))
      .getOrElse(SMono.just[RateLimitingResult](AcceptableRate))
  }
}

/**
 * <p><b>GlobalRateLimit</b> allows defining and enforcing rate limits for all users.</p>
 *
 * <ul>This allows writing rules like:
 *   <li>All users can send 100 emails per hour</li>
 *   <li>All users can send email to a total of 200 recipients per hour</li>
 *   <li>All users can send 1000 MB of emails (total computed taking only the email size into account) per hour</li>
 *   <li>All users can send 2000 MB of emails (total computed taking each recipient copies into account) per hour</li>
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
 *    <li><b>precision</b>: Defines the time precision that will be used to approximate the sliding window. A lower duration increases precision but requests more computing power. The precision must be greater than 1 second. Optional, default to duration means fixed window counters. Supported units includes s (second), m (minute), h (hour), d (day).</li>
 *    <li><b>count</b>: Count of emails allowed for all users during duration. Optional, if unspecified this rate limit is not applied.</li>
 *    <li><b>recipients</b>: Count of recipients allowed for all users during duration. Optional, if unspecified this rate limit is not applied.</li>
 *    <li><b>size</b>: Size of emails allowed for all users during duration (each email count one time, regardless of recipient count). Optional, if unspecified this rate limit is not applied. Supported units : B ( 2^0 ), K ( 2^10 ), M ( 2^20 ), G ( 2^30 ), defaults to B.</li>
 *    <li><b>totalSize</b>: Size of emails allowed for all users during duration (each recipient of the email email count one time). Optional, if unspecified this rate limit is not applied. Supported units : B ( 2^0 ), K ( 2^10 ), M ( 2^20 ), G ( 2^30 ), defaults to B. Note that
 *    totalSize is limited in increments of 2exp(31) - ~2 billions: sending a 10MB file to more than 205 recipients will be rejected if this parameter is enabled.</li>
 *  </ul>
 *
 *  <p>For instance, to apply all the examples given above:</p>
 *
 *   <pre><code>
 * &lt;mailet matcher=&quot;All&quot; class=&quot;GlobalRateLimit&quot;&gt;
 *     &lt;keyPrefix&gt;myPrefix&lt;/keyPrefix&gt;
 *     &lt;duration&gt;1h&lt;/duration&gt;
 *     &lt;precision&gt;1h&lt;/precision&gt;
 *     &lt;count&gt;10&lt;/count&gt;
 *     &lt;recipients&gt;20&lt;/recipients&gt;
 *     &lt;size&gt;100M&lt;/size&gt;
 *     &lt;totalSize&gt;200M&lt;/totalSize&gt;
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
class GlobalRateLimit @Inject()(rateLimiterFactory: RateLimiterFactory) extends GenericMailet {
  private var countRateLimiter: GlobalRateLimiter = _
  private var recipientsRateLimiter: GlobalRateLimiter = _
  private var sizeRateLimiter: GlobalRateLimiter = _
  private var totalSizeRateLimiter: GlobalRateLimiter = _
  private var exceededProcessor: String = _
  private var keyPrefix: Option[KeyPrefix] = _

  override def init(): Unit = {
    import org.apache.james.transport.mailets.ConfigurationOps.DurationOps

    val duration: Duration = getMailetConfig.getDuration("duration")
      .getOrElse(throw new IllegalArgumentException("'duration' is compulsory"))

    val precision: Option[Duration] = getMailetConfig.getDuration("precision")

    keyPrefix = Option(getInitParameter("keyPrefix")).map(KeyPrefix)
    exceededProcessor = getInitParameter("exceededProcessor", Mail.ERROR)

    def globalRateLimiter(entityType: EntityType): GlobalRateLimiter = createRateLimiter(rateLimiterFactory, entityType, keyPrefix, duration, precision)

    countRateLimiter = globalRateLimiter(Count)
    recipientsRateLimiter = globalRateLimiter(RecipientsType)
    sizeRateLimiter = globalRateLimiter(Size)
    totalSizeRateLimiter = globalRateLimiter(TotalSize)
  }

  override def service(mail: Mail): Unit = {
    val pivot: RateLimitingResult = AcceptableRate
    val result = SFlux.merge(Seq(
      countRateLimiter.rateLimit(mail),
      recipientsRateLimiter.rateLimit(mail),
      sizeRateLimiter.rateLimit(mail),
      totalSizeRateLimiter.rateLimit(mail)))
      .fold(pivot)((a, b) => a.merge(b))
      .block()

    if (result.equals(RateExceeded)) {
      mail.setState(exceededProcessor)
    }
  }

  private def createRateLimiter(rateLimiterFactory: RateLimiterFactory, entityType: EntityType, keyPrefix: Option[KeyPrefix],
                                duration: Duration, precision: Option[Duration]): GlobalRateLimiter =
    new GlobalRateLimiter(rateLimiter = EntityType.extractRules(entityType, duration, getMailetConfig)
      .map(rateLimiterFactory.withSpecification(_, precision)),
      keyPrefix = keyPrefix,
      entityType = entityType)


  override def requiredProcessingState(): util.Collection[ProcessingState] = ImmutableList.of(new ProcessingState(exceededProcessor))
}
