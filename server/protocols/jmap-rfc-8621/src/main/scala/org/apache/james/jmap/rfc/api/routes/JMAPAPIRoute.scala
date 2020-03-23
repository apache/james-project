package org.apache.james.jmap.rfc.api.routes

import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import org.reactivestreams.Publisher
import reactor.netty.http.server.HttpServerRequest

case class MethodName(value: String Refined NonEmpty)
trait JMAPAPIRoute[T] {
  var methodName: MethodName
  def process(httpServerRequest: HttpServerRequest):Publisher[T]
}

