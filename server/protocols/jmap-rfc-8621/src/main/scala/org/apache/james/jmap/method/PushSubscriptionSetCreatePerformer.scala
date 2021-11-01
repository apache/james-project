package org.apache.james.jmap.method

import javax.inject.Inject
import org.apache.james.jmap.api.model.{DeviceClientIdInvalidException, ExpireTimeInvalidException, PushSubscriptionCreationRequest, PushSubscriptionExpiredTime}
import org.apache.james.jmap.api.pushsubscription.PushSubscriptionRepository
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{PushSubscriptionCreation, PushSubscriptionCreationId, PushSubscriptionCreationParseException, PushSubscriptionCreationResponse, PushSubscriptionSetRequest, SetError}
import org.apache.james.jmap.json.PushSubscriptionSerializer
import org.apache.james.jmap.method.PushSubscriptionSetCreatePerformer.{CreationFailure, CreationResult, CreationResults, CreationSuccess}
import org.apache.james.mailbox.MailboxSession
import play.api.libs.json.{JsError, JsObject, JsPath, JsSuccess, JsonValidationError}
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

object PushSubscriptionSetCreatePerformer {
  trait CreationResult

  case class CreationSuccess(clientId: PushSubscriptionCreationId, response: PushSubscriptionCreationResponse) extends CreationResult

  case class CreationFailure(clientId: PushSubscriptionCreationId, e: Throwable) extends CreationResult {
    def asMessageSetError: SetError = e match {
      case e: PushSubscriptionCreationParseException => e.setError
      case e: ExpireTimeInvalidException => SetError.invalidArguments(SetErrorDescription(e.getMessage))
      case e: DeviceClientIdInvalidException => SetError.invalidArguments(SetErrorDescription(e.getMessage))
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(e.getMessage))
      case _ => SetError.serverFail(SetErrorDescription(e.getMessage))
    }
  }

  case class CreationResults(results: Seq[CreationResult]) {
    def created: Option[Map[PushSubscriptionCreationId, PushSubscriptionCreationResponse]] =
      Option(results.flatMap {
        case result: CreationSuccess => Some((result.clientId, result.response))
        case _ => None
      }.toMap)
        .filter(_.nonEmpty)

    def notCreated: Option[Map[PushSubscriptionCreationId, SetError]] = {
      Option(results.flatMap {
        case failure: CreationFailure => Some((failure.clientId, failure.asMessageSetError))
        case _ => None
      }
        .toMap)
        .filter(_.nonEmpty)
    }
  }
}

class PushSubscriptionSetCreatePerformer @Inject()(pushSubscriptionRepository: PushSubscriptionRepository,
                                                  pushSubscriptionSerializer: PushSubscriptionSerializer) {
  def create(request: PushSubscriptionSetRequest, mailboxSession: MailboxSession): SMono[CreationResults] =
    SFlux.fromIterable(request.create.getOrElse(Map()))
      .concatMap {
        case (clientId, json) => parseCreate(json)
          .fold(e => SMono.just[CreationResult](CreationFailure(clientId, e)),
            creationRequest => create(clientId, creationRequest, mailboxSession))
      }.collectSeq()
      .map(CreationResults)

  private def parseCreate(jsObject: JsObject): Either[PushSubscriptionCreationParseException, PushSubscriptionCreationRequest] =
    PushSubscriptionCreation.validateProperties(jsObject)
      .flatMap(validJsObject => pushSubscriptionSerializer.deserializePushSubscriptionCreationRequest(validJsObject) match {
        case JsSuccess(creationRequest, _) =>
          creationRequest.validate match {
            case Left(e) => Left(PushSubscriptionCreationParseException(SetError.invalidArguments(SetErrorDescription(e.getMessage))))
            case Right(validSuccess) => Right(validSuccess)
          }
        case JsError(errors) => Left(PushSubscriptionCreationParseException(pushSubscriptionSetError(errors)))
      })

  private def create(clientId: PushSubscriptionCreationId, request: PushSubscriptionCreationRequest, mailboxSession: MailboxSession): SMono[CreationResult] =
    SMono.fromPublisher(pushSubscriptionRepository.save(mailboxSession.getUser, request))
      .map(subscription => CreationSuccess(clientId, PushSubscriptionCreationResponse(subscription.id, showExpires(subscription.expires, request))))
      .onErrorResume(e => SMono.just[CreationResult](CreationFailure(clientId, e)))
      .subscribeOn(Schedulers.elastic)

  private def showExpires(expires: PushSubscriptionExpiredTime, request: PushSubscriptionCreationRequest): Option[PushSubscriptionExpiredTime] = request.expires match {
    case Some(requestExpires) if expires.eq(requestExpires) => None
    case _ => Some(expires)
  }

  private def pushSubscriptionSetError(errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]): SetError =
    errors.head match {
      case (path, Seq()) => SetError.invalidArguments(SetErrorDescription(s"'$path' property in PushSubscription object is not valid"))
      case (path, Seq(JsonValidationError(Seq("error.path.missing")))) => SetError.invalidArguments(SetErrorDescription(s"Missing '$path' property in PushSubscription object"))
      case (path, Seq(JsonValidationError(Seq(message)))) => SetError.invalidArguments(SetErrorDescription(s"'$path' property in PushSubscription object is not valid: $message"))
      case (path, _) => SetError.invalidArguments(SetErrorDescription(s"Unknown error on property '$path'"))
    }
}
