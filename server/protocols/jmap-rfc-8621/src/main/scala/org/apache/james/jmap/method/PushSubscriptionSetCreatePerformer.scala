package org.apache.james.jmap.method

import java.nio.charset.StandardCharsets

import eu.timepit.refined.auto._
import javax.inject.Inject
import org.apache.james.jmap.api.model.{DeviceClientIdInvalidException, ExpireTimeInvalidException, PushSubscriptionCreationRequest, PushSubscriptionExpiredTime, PushSubscriptionId, PushSubscriptionKeys, PushSubscriptionServerURL, VerificationCode}
import org.apache.james.jmap.api.pushsubscription.PushSubscriptionRepository
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Properties, PushSubscriptionCreation, PushSubscriptionCreationId, PushSubscriptionCreationParseException, PushSubscriptionCreationResponse, PushSubscriptionSetRequest, SetError}
import org.apache.james.jmap.json.{PushSerializer, PushSubscriptionSerializer}
import org.apache.james.jmap.method.PushSubscriptionSetCreatePerformer.{CreationFailure, CreationResult, CreationResults, CreationSuccess}
import org.apache.james.jmap.pushsubscription.{PushRequest, PushTTL, WebPushClient}
import org.apache.james.mailbox.MailboxSession
import play.api.libs.json.{JsObject, JsPath, Json, JsonValidationError}
import reactor.core.scala.publisher.{SFlux, SMono}

object PushSubscriptionSetCreatePerformer {
  trait CreationResult

  case class CreationSuccess(clientId: PushSubscriptionCreationId, response: PushSubscriptionCreationResponse) extends CreationResult

  case class CreationFailure(clientId: PushSubscriptionCreationId, e: Throwable) extends CreationResult {
    def asMessageSetError: SetError = e match {
      case e: PushSubscriptionCreationParseException => e.setError
      case e: ExpireTimeInvalidException => SetError.invalidArguments(SetErrorDescription(e.getMessage), Some(Properties("expires")))
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
                                                   pushSubscriptionSerializer: PushSubscriptionSerializer,
                                                   verificationCreateProcessor: PushSubscriptionSetCreateProcessor) {
  def create(request: PushSubscriptionSetRequest, mailboxSession: MailboxSession): SMono[CreationResults] =
    SFlux.fromIterable(request.create.getOrElse(Map()))
      .concatMap {
        case (clientId, json) => parseCreate(json)
          .fold(e => SMono.just[CreationResult](CreationFailure(clientId, e)),
            creationRequest => create(clientId, creationRequest, mailboxSession))
      }.collectSeq()
      .map(CreationResults)

  private def parseCreate(jsObject: JsObject): Either[Exception, PushSubscriptionCreationRequest] = for {
      validJsObject <- PushSubscriptionCreation.validateProperties(jsObject)
      parsedRequest <-  pushSubscriptionSerializer.deserializePushSubscriptionCreationRequest(validJsObject).asEither
        .left.map(errors => PushSubscriptionCreationParseException(pushSubscriptionSetError(errors)))
      validatedRequest <- parsedRequest.validate
        .left.map(e => PushSubscriptionCreationParseException(SetError.invalidArguments(SetErrorDescription(e.getMessage))))
    } yield {
      validatedRequest
    }

  private def create(clientId: PushSubscriptionCreationId, request: PushSubscriptionCreationRequest, mailboxSession: MailboxSession): SMono[CreationResult] =
    SMono.fromPublisher(pushSubscriptionRepository.save(mailboxSession.getUser, request))
      .flatMap(subscription => verificationCreateProcessor.pushVerificationToPushServer(subscription.url,
        PushVerification(subscription.id, subscription.verificationCode), request.keys)
        .onErrorResume(error =>
          SMono.fromPublisher(pushSubscriptionRepository.revoke(mailboxSession.getUser, subscription.id))
            .`then`(SMono.error(error)))
        .`then`(SMono.just(subscription)))
      .map(subscription => CreationSuccess(clientId, PushSubscriptionCreationResponse(subscription.id, showExpires(subscription.expires, request))))
      .onErrorResume(e => SMono.just[CreationResult](CreationFailure(clientId, e)))

  private def showExpires(expires: PushSubscriptionExpiredTime, request: PushSubscriptionCreationRequest): Option[PushSubscriptionExpiredTime] = request.expires match {
    case Some(requestExpires) if expires.eq(requestExpires) => None
    case _ => Some(expires)
  }

  private def pushSubscriptionSetError(errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]): SetError = standardError(errors)
}

class PushSubscriptionSetCreateProcessor @Inject()(webPushClient: WebPushClient) {

  def pushVerificationToPushServer(pushSubscriptionServerURL: PushSubscriptionServerURL,
                                   pushVerification: PushVerification,
                                   keys: Option[PushSubscriptionKeys]): SMono[Unit] =

    SMono.fromCallable(() => Json.stringify(PushSerializer.serializePushVerification(pushVerification)).getBytes(StandardCharsets.UTF_8))
      .map(clearPayload => keys.map(keysValue => keysValue.encrypt(clearPayload)).getOrElse(clearPayload))
      .flatMap(payload => SMono.fromPublisher(webPushClient.push(pushSubscriptionServerURL, PushRequest(PushTTL.MAX, payload = payload))))
}

case class PushVerification(pushSubscriptionId: PushSubscriptionId,
                            verificationCode: VerificationCode)
