package org.apache.james.jmap.json

import eu.timepit.refined.auto._
import org.apache.james.jmap.model.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.model.CreatedIds.{ClientId, ServerId}
import org.apache.james.jmap.model.Id.Id
import org.apache.james.jmap.model.Invocation.{Arguments, MethodCallId, MethodName}
import org.apache.james.jmap.model.{CreatedIds, Invocation}
import play.api.libs.json.Json

object Fixture {
  val id: Id = "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8"
  val createdIds: CreatedIds = CreatedIds(Map(ClientId(id) -> ServerId(id)))
  val coreIdentifier: CapabilityIdentifier = "urn:ietf:params:jmap:core"
  val mailIdentifier: CapabilityIdentifier = "urn:ietf:params:jmap:mail"
  val invocation1: Invocation = Invocation(
    methodName = MethodName("Core/echo1"),
    arguments = Arguments(Json.obj("arg1" -> "arg1data", "arg2" -> "arg2data")),
    methodCallId = MethodCallId("c1"))
  val invocation2: Invocation = Invocation(
    methodName = MethodName("Core/echo2"),
    arguments = Arguments(Json.obj("arg3" -> "arg3data", "arg4" -> "arg4data")),
    methodCallId = MethodCallId("c2")
  )
}
