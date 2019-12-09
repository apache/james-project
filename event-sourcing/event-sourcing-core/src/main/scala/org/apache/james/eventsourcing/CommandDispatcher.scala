 /***************************************************************
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
package org.apache.james.eventsourcing

import com.google.common.base.Preconditions
import javax.inject.Inject
import org.apache.james.eventsourcing.eventstore.EventStoreFailedException

import scala.util.{Failure, Success, Try}

object CommandDispatcher {
  private val MAX_RETRY = 10
  private val ONLY_ONE_HANDLER_PRECONDITION: Object = "There should exist only one handler by command"

  case class UnknownCommandException(command: Command)
    extends RuntimeException(String.format("Unknown command %s", command)) {
    def getCommand: Command = command
  }

  case class TooManyRetries(command: Command, retries: Int)
    extends RuntimeException(String.format("Too much retries for command %s. Store failure after %d retries", command, retries)) {
    def getCommand: Command = command

    def getRetries: Int = retries
  }

}

class CommandDispatcher @Inject()(eventBus: EventBus, handlers: Set[CommandHandler[_ <: Command]]) {
  Preconditions.checkArgument(hasOnlyOneHandlerByCommand(handlers), CommandDispatcher.ONLY_ONE_HANDLER_PRECONDITION)

  def dispatch(c: Command): Unit = {
    trySeveralTimes(() => tryDispatch(c))
      .getOrElse(() => throw CommandDispatcher.TooManyRetries(c, CommandDispatcher.MAX_RETRY))
  }

  private def hasOnlyOneHandlerByCommand(handlers: Set[CommandHandler[_ <: Command]]): Boolean =
    handlers.groupBy(_.handledClass)
      .values
      .forall(_.size == 1)

  private val handlersByClass: Map[Class[_ <: Command], CommandHandler[_ <: Command]] =
    handlers.map(handler => (handler.handledClass, handler)).toMap


  private def trySeveralTimes(singleTry: () => Boolean): Option[Unit] =
    0.until(CommandDispatcher.MAX_RETRY)
      .find(_ => singleTry())
      .map(_ => ())


  private def tryDispatch(c: Command): Boolean = {
    val maybeEvents: Option[Try[List[_ <: Event]]] = handleCommand(c)
    maybeEvents match {
      case Some(eventsTry) =>
        eventsTry
          .flatMap(events => Try(eventBus.publish(events))) match {
          case Success(_) => true
          case Failure(_: EventStoreFailedException) => false
          case Failure(e) => throw e
        }
      case _ =>
        throw CommandDispatcher.UnknownCommandException(c)
    }
  }

  private def handleCommand(c: Command): Option[Try[List[_ <: Event]]] = {
    handlersByClass
      .get(c.getClass)
      .map(commandHandler =>
        Try(
          commandHandler
            .asInstanceOf[CommandHandler[c.type]]
            .handle(c)))
  }
}