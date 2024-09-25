package org.apache.james.rate.limiter

import java.time.Duration

import eu.timepit.refined.auto._
import org.apache.james.backends.redis.RedisConfiguration
import org.apache.james.rate.limiter.TopologyRedisRateLimiterTest.{RULES, SLIDING_WIDOW_PRECISION}
import org.apache.james.rate.limiter.api.{AcceptableRate, RateLimitingResult, Rule, Rules, TestKey}
import org.apache.james.rate.limiter.redis.RedisRateLimiterFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.SMono

object TopologyRedisRateLimiterTest {
  val SLIDING_WIDOW_PRECISION: Option[Duration] = Some(Duration.ofSeconds(1))
  val RULES = Rules(Seq(Rule(4L, Duration.ofSeconds(2))))
}

trait TopologyRedisRateLimiterTest {
  def getRedisConfiguration(): RedisConfiguration

  @Test
  def rateLimitShouldWorkNormally(): Unit = {
    val rateLimiterFactory: RedisRateLimiterFactory = new RedisRateLimiterFactory(getRedisConfiguration())
    val rateLimiter = rateLimiterFactory.withSpecification(RULES, SLIDING_WIDOW_PRECISION)
    val actual: RateLimitingResult = SMono(rateLimiter.rateLimit(TestKey("key1"), 4)).block()
    assertThat(actual).isEqualTo(AcceptableRate)
  }
}
