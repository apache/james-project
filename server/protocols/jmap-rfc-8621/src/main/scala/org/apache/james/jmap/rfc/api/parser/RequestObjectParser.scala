package org.apache.james.jmap.rfc.api.parser

import java.io.InputStream

import org.apache.james.jmap.rfc.model.RequestObject
import play.api.libs.json.{JsError, JsSuccess, Json}
import reactor.core.publisher.Mono

class RequestObjectParser {
  def toRequestObject(inputStream: InputStream): Mono[RequestObject] = {
    Json.fromJson[RequestObject](Json.parse(inputStream)) match {
      case JsSuccess(requestObject, _) => Mono.just(requestObject)
      case JsError(errors) => Mono.error(new RuntimeException(errors.toString()))
    }
  }
}
