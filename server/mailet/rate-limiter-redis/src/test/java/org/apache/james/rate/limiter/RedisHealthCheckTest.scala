package org.apache.james.rate.limiter

import org.apache.james.core.healthcheck.Result
import org.apache.james.rate.limiter.redis.{RedisHealthCheck, RedisRateLimiterConfiguration, RedisRateLimiterFactory}
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.{AfterEach, BeforeEach, Test}
import reactor.core.scala.publisher.SMono

@ExtendWith(Array(classOf[RedisExtension]))
class RedisHealthCheckTest {

  var redisHealthCheck: RedisHealthCheck = _

  @BeforeEach
  def setup(redis: DockerRedis): Unit = {
    val redisRateLimiterFactory: RedisRateLimiterFactory = new RedisRateLimiterFactory(
      RedisRateLimiterConfiguration.from(redis.redisURI().toString, false))

    redisHealthCheck = new RedisHealthCheck(redisRateLimiterFactory)
  }

  @AfterEach
  def cleaner(redis: DockerRedis): Unit =
    if (redis.isPaused) {
      redis.unPause()
    }

  @Test
  def checkShouldReturnHealthyWhenRedisIsRunning(): Unit = {
    val result: Result = SMono.fromPublisher(redisHealthCheck.check()).block()

    assertThat(result.isHealthy).isTrue
  }

  @Test
  def multipleCheckInShortPeriodShouldReturnHealthyWhenRedisIsRunning(): Unit = {
    val check1: Result = SMono.fromPublisher(redisHealthCheck.check()).block()
    val check2: Result = SMono.fromPublisher(redisHealthCheck.check()).block()
    val check3: Result = SMono.fromPublisher(redisHealthCheck.check()).block()

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(check1.isHealthy).isTrue
      softly.assertThat(check2.isHealthy).isTrue
      softly.assertThat(check3.isHealthy).isTrue
    })
  }

  @Test
  def checkShouldReturnUnhealthyWhenRedisIsDown(redis: DockerRedis): Unit = {
    redis.pause()
    val result: Result = SMono.fromPublisher(redisHealthCheck.check()).block()

    assertThat(result.isUnHealthy).isTrue
  }

  @Test
  def checkShouldReturnHealthyWhenRedisIsRecovered(redis: DockerRedis): Unit = {
    redis.pause()
    redis.unPause()
    val result: Result = SMono.fromPublisher(redisHealthCheck.check()).block()

    assertThat(result.isHealthy).isTrue
  }

  @Test
  def multipleCheckInShortPeriodShouldReturnMixedResultWhenRedisIsUnstable(redis: DockerRedis): Unit = {
    val check1: Result = SMono.fromPublisher(redisHealthCheck.check()).block()

    redis.pause()
    val check2: Result = SMono.fromPublisher(redisHealthCheck.check()).block()

    redis.unPause()
    val check3: Result = SMono.fromPublisher(redisHealthCheck.check()).block()

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(check1.isHealthy).isTrue
      softly.assertThat(check2.isUnHealthy).isTrue
      softly.assertThat(check3.isHealthy).isTrue
    })
  }

}
