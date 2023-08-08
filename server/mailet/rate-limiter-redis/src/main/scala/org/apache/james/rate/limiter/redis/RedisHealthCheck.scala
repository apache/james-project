package org.apache.james.rate.limiter.redis

import java.time.Duration
import java.util.concurrent.TimeUnit

import eu.timepit.refined.auto._
import javax.inject.Inject
import org.apache.james.core.healthcheck.{ComponentName, HealthCheck, Result}
import org.apache.james.rate.limiter.api.{RateLimitingKey, Rule, Rules}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

import scala.concurrent.duration.FiniteDuration

sealed class RedisHealthCheckKey() extends RateLimitingKey {
  override def asString(): String = "RedisHealthCheck"
}

class RedisHealthCheck @Inject()(redisRateLimiterFactory: RedisRateLimiterFactory) extends HealthCheck {
  private val COMPONENT_NAME: ComponentName = new ComponentName("Redis")
  private val REDIS_HEALTHCHECK_KEY: RedisHealthCheckKey = new RedisHealthCheckKey
  private val FLEXIBLE_RATE_LIMIT_RULE = Rules(Seq(Rule(100000L, Duration.ofSeconds(60))))
  private val REDIS_HEALTHCHECK_RATE_LIMITER = redisRateLimiterFactory.withSpecification(FLEXIBLE_RATE_LIMIT_RULE, None)
  private val HEALTH_CHECK_TIMEOUT = new FiniteDuration(3, TimeUnit.SECONDS)

  override def componentName(): ComponentName = COMPONENT_NAME

  override def check(): Publisher[Result] =
    SMono.fromPublisher(REDIS_HEALTHCHECK_RATE_LIMITER.rateLimit(REDIS_HEALTHCHECK_KEY, 1))
      .timeout(HEALTH_CHECK_TIMEOUT)
      .`then`(SMono.just(Result.healthy(COMPONENT_NAME)))
      .onErrorResume(e => SMono.just(Result.unhealthy(COMPONENT_NAME, "Can not connect to Redis.", e)))
}
