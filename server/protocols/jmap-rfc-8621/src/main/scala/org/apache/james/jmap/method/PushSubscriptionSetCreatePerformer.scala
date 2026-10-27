package org.apache.james.jmap.method

import java.nio.charset.StandardCharsets

import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.jmap.api.model.{DeviceClientIdInvalidException, ExpireTimeInvalidException, PushSubscriptionCreationRequest, PushSubscriptionExpiredTime, PushSubscriptionId, PushSubscriptionKeys, PushSubscriptionServerURL, VerificationCode}
import org.apache.james.jmap.api.pushsubscription.PushSubscriptionRepository
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Properties, PushSubscriptionCreation, PushSubscriptionCreationId, PushSubscriptionCreationParseException, PushSubscriptionCreationResponse, PushSubscriptionSetRequest, SetError}
import org.apache.james.jmap.json.{PushSerializer, PushSubscriptionSerializer}
import org.apache.james.jmap.method.PushSubscriptionSetCreatePerformer.{CreationFailure, CreationResult, CreationSuccess}
import org.apache.james.jmap.pushsubscription.{Aes128gcm, PushRequest, PushTTL, WebPushClient}
import org.apache.james.jmap.routes.{CreationResults, ProcessingContext}
import org.apache.james.mailbox.MailboxSession
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsObject, JsPath, Json, JsonValidationError}
import reactor.core.scala.publisher.SMono

object PushSubscriptionSetCreatePerformer {
  private val LOGGER = LoggerFactory.getLogger(classOf[PushSubscriptionSetCreatePerformer])
  trait CreationResult

  case class CreationSuccess(clientId: PushSubscriptionCreationId, response: PushSubscriptionCreationResponse) extends CreationResult

  case class CreationFailure(clientId: PushSubscriptionCreationId, e: Throwable) extends CreationResult {
    def asMessageSetError: SetError = e match {
      case e: PushSubscriptionCreationParseException =>
        LOGGER.info("Failure to parse PushSubscription/set create", e)
        e.setError
      case e: ExpireTimeInvalidException =>
        LOGGER.info("Invalid time expiracy for push subscription: {}", e.getMessage)
        SetError.invalidArguments(SetErrorDescription(e.getMessage), Some(Properties("expires")))
      case e: DeviceClientIdInvalidException =>
        LOGGER.info("Invalid deviceId for push subscription: {}", e.getMessage)
        SetError.invalidArguments(SetErrorDescription(e.getMessage))
      case e: IllegalArgumentException =>
        LOGGER.info("Illegal argument in PushSubscription/set create", e)
        SetError.invalidArguments(SetErrorDescription(e.getMessage))
      case e =>
        LOGGER.error("Failed to create push subscription", e)
        SetError.serverFail(SetErrorDescription(e.getMessage))
    }
  }


}

class PushSubscriptionSetCreatePerformer @Inject()(pushSubscriptionRepository: PushSubscriptionRepository,
                                                   pushSubscriptionSerializer: PushSubscriptionSerializer,
                                                   verificationCreateProcessor: PushSubscriptionSetCreateProcessor) {
  def create(request: PushSubscriptionSetRequest, mailboxSession: MailboxSession,
             processingContext: ProcessingContext): SMono[CreationResults[PushSubscriptionCreationId, PushSubscriptionCreationResponse]] =
    processingContext.processCreations[PushSubscriptionCreationId, PushSubscriptionCreationResponse](request.create.getOrElse(Map.empty))(
      _.id, _.id.value.toString) { (clientId, json) =>
      parseCreate(json)
        .fold(e => SMono.just[CreationResult](CreationFailure(clientId, e)),
          creationRequest => create(clientId, creationRequest, mailboxSession))
        .map {
          case CreationSuccess(_, response) => Right(response)
          case failure: CreationFailure => Left(failure.asMessageSetError)
        }
    }

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
      .flatMap(payload => SMono.fromPublisher(webPushClient.push(pushSubscriptionServerURL, PushRequest(
        ttl = PushTTL.ONE_WEEK,
        // Without it the client cannot tell that the payload is encrypted, nor read it
        contentCoding = keys.map(_ => Aes128gcm),
        payload = payload))))
}

case class PushVerification(pushSubscriptionId: PushSubscriptionId,
                            verificationCode: VerificationCode)
