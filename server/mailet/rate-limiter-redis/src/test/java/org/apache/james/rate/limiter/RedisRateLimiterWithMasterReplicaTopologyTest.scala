package org.apache.james.rate.limiter

import java.time.Duration

import eu.timepit.refined.auto._
import org.apache.james.backends.redis.RedisMasterReplicaExtension
import org.apache.james.rate.limiter.RedisRateLimiterWithMasterReplicaTopologyTest.{RULES, SLIDING_WIDOW_PRECISION}
import org.apache.james.rate.limiter.api.{AcceptableRate, RateLimitingKey, RateLimitingResult, Rule, Rules}
import org.apache.james.rate.limiter.redis.RedisRateLimiterFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.SMono

object RedisRateLimiterWithMasterReplicaTopologyTest {
  val SLIDING_WIDOW_PRECISION: Option[Duration] = Some(Duration.ofSeconds(1))
  val RULES = Rules(Seq(Rule(4L, Duration.ofSeconds(2))))
}

@ExtendWith(Array(classOf[RedisMasterReplicaExtension]))
class RedisRateLimiterWithMasterReplicaTopologyTest {
  var rateLimiterFactory: RedisRateLimiterFactory = _

  @BeforeEach
  def setup(redisClusterContainer: RedisMasterReplicaExtension.RedisClusterContainer): Unit = {
    rateLimiterFactory = new RedisRateLimiterFactory(redisClusterContainer.getRedisConfiguration)
  }

  @Test
  def test(redisClusterContainer: RedisMasterReplicaExtension.RedisClusterContainer): Unit = {
    val rateLimiterFactory = new RedisRateLimiterFactory(redisClusterContainer.getRedisConfiguration)
    val rateLimiter = rateLimiterFactory.withSpecification(RULES, SLIDING_WIDOW_PRECISION)
    val actual: RateLimitingResult = SMono(rateLimiter.rateLimit(TestKey("key1"), 4)).block()
    assertThat(actual).isEqualTo(AcceptableRate)
  }
}

case class TestKey(value: String) extends RateLimitingKey {
  override def asString: String = value
}
