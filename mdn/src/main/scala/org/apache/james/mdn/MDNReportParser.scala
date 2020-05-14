/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *    http://www.apache.org/licenses/LICENSE-2.0                *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mdn

import java.io.InputStream

import org.apache.commons.io.IOUtils
import org.apache.james.mdn.`type`.DispositionType
import org.apache.james.mdn.action.mode.DispositionActionMode
import org.apache.james.mdn.fields._
import org.apache.james.mdn.modifier.DispositionModifier
import org.apache.james.mdn.sending.mode.DispositionSendingMode
import org.parboiled2._
import org.slf4j.LoggerFactory
import shapeless.HNil

import scala.util.{Failure, Try}

object MDNReportParser {
  private val LOGGER = LoggerFactory.getLogger(classOf[MDNReportParser])

  def parse(is: InputStream, charset: String): Try[MDNReport] = new MDNReportParser(IOUtils.toString(is, charset)).dispositionNotificationContent.run()

  def parse(input : String): Try[MDNReport] = {
    val parser = new MDNReportParser(input)
    val result = parser.dispositionNotificationContent.run()

    result match {
      case res@Failure(e : ParseError) =>
        LOGGER.debug(parser.formatError(e))
        res
      case res => res
    }
  }
}

class MDNReportParser(val input: ParserInput) extends Parser {

  /*    disposition-notification-content =
                     [ reporting-ua-field CRLF ]
                     [ mdn-gateway-field CRLF ]
                     [ original-recipient-field CRLF ]
                     final-recipient-field CRLF
                     [ original-message-id-field CRLF ]
                     disposition-field CRLF
                     *( error-field CRLF )
                     *( extension-field CRLF )    */
  private def dispositionNotificationContent: Rule1[MDNReport] = rule {
    (
      (reportingUaField ~ crlf).? ~
        (mdnGatewayField ~ crlf).? ~
        (originalRecipientField ~ crlf).? ~
        finalRecipientField ~ crlf ~
        (originalMessageIdField ~ crlf).? ~
        dispositionField ~ crlf ~
        zeroOrMore(errorField ~ crlf) ~
        zeroOrMore(extentionField ~ crlf)
      ) ~> ((reportingUserAgent : Option[ReportingUserAgent],
             gateway : Option[Gateway],
             originalRecipient : Option[OriginalRecipient],
             finalRecipient: FinalRecipient,
             originalMessageId: Option[OriginalMessageId],
             disposition: Disposition,
             errors: Seq[Error],
             extensions: Seq[ExtensionField]) => {
      val builder = MDNReport.builder()
        .finalRecipientField(finalRecipient)
        .dispositionField(disposition)
        .addErrorFields(errors:_*)
        .withExtensionFields(extensions:_*)

      val builderWithUa = reportingUserAgent.fold(builder)(builder.reportingUserAgentField)
      val builderWithGateway = gateway.fold(builderWithUa)(builder.gatewayField)
      val builderWithOriginalRecipent = originalRecipient.fold(builderWithGateway)(builder.originalRecipientField)
      val builderWithOriginalMessageId = originalMessageId.fold(builderWithOriginalRecipent)(builder.originalMessageIdField)
      builderWithOriginalMessageId.build()
    })
  }

  /*    reporting-ua-field = "Reporting-UA" ":" OWS ua-name OWS [
                                   ";" OWS ua-product OWS ]    */
  private[mdn] def reportingUaField: Rule1[ReportingUserAgent] = rule {
    ("Reporting-UA" ~ ":" ~ ows ~ capture(uaName) ~ ows ~ (";" ~ ows ~ capture(uaProduct) ~ ows).?) ~> ((uaName: String, uaProduct: Option[String]) => {
     val builder = ReportingUserAgent.builder()
        .userAgentName(uaName)
      (uaProduct match {
         case Some(product) => builder.userAgentProduct(product)
         case None => builder
       }).build()
    })
  }

  //    ua-name = *text-no-semi
  private def uaName: Rule0 = rule { zeroOrMore(textNoSemi) }

  /*    text-no-semi = %d1-9 /        ; "text" characters excluding NUL, CR,
                             %d11 / %d12 / %d14-58 / %d60-127      ; LF, or semi-colon    */
  private def textNoSemi: Rule0 = rule {
    CharPredicate(1.toChar to 9.toChar) |
      ch(11) |
      ch(12) |
      CharPredicate(14.toChar to 58.toChar) |
      CharPredicate(60.toChar to 127.toChar)
  }

  //    ua-product = *([FWS] text)
  private def uaProduct: Rule0 = rule { zeroOrMore(fws.? ~ text) }

  /*   text            =   %d1-9 /            ; Characters excluding CR
                                 %d11 /             ;  and LF
                                 %d12 /
                                 %d14-127   */
  private def text = rule {
    CharPredicate(1.toChar to 9.toChar) |
      ch(11) |
      ch(12) |
      CharPredicate(14.toChar to 127.toChar)
  }

  /*    OWS = [CFWS]
            ; Optional whitespace.
            ; MDN generators SHOULD use "*WSP"
            ; (Typically a single space or nothing.
            ; It SHOULD be nothing at the end of a field.),
            ; unless an RFC 5322 "comment" is required.
            ;
            ; MDN parsers MUST parse it as "[CFWS]".    */
  private def ows = rule {
    cfws.?
  }

  /*    mdn-gateway-field = "MDN-Gateway" ":" OWS mta-name-type OWS
                                  ";" OWS mta-name    */
  def mdnGatewayField : Rule1[Gateway] = rule {
    ("MDN-Gateway" ~ ":" ~ ows ~ capture(mtaNameType) ~ ows ~ ";" ~ ows ~ capture(mtaName) ~ ows) ~> ((gatewayType : String, name : String) => Gateway
      .builder()
      .name(Text.fromRawText(name))
      .nameType(new AddressType(gatewayType))
      .build())
  }

  //    mta-name-type = Atom
  private def mtaNameType = rule { atom }

  //    mta-name = *text
  private def mtaName = rule { zeroOrMore(text) }

  /*    original-recipient-field =
                     "Original-Recipient" ":" OWS address-type OWS
                     ";" OWS generic-address OWS    */
  private[mdn] def originalRecipientField : Rule1[OriginalRecipient] = rule {
    ("Original-Recipient" ~ ":" ~ ows ~ capture(addressType) ~ ows ~ ";" ~ ows ~ capture(genericAddress) ~ ows) ~> ((addrType : String, genericAddr : String) =>
      OriginalRecipient
        .builder()
      .addressType(new AddressType(addrType))
      .originalRecipient(Text.fromRawText(genericAddr))
      .build()
      )
  }

  //    address-type = Atom
  private def addressType = rule { atom }

  //    generic-address = *text
  private def genericAddress = rule { zeroOrMore(text) }

  /*    final-recipient-field =
             "Final-Recipient" ":" OWS address-type OWS
             ";" OWS generic-address OWS    */
  private[mdn] def finalRecipientField : Rule1[FinalRecipient] = rule {
    ("Final-Recipient" ~ ":" ~ ows ~ capture(addressType) ~ ows ~ ";" ~ ows ~ capture(genericAddress) ~ ows) ~> ((addrType : String, genericAddr : String) =>
    FinalRecipient
      .builder()
      .addressType(new AddressType(addrType))
      .finalRecipient(Text.fromRawText(genericAddr))
      .build()
    )
  }

  //    original-message-id-field = "Original-Message-ID" ":" msg-id
  private[mdn] def originalMessageIdField: Rule1[OriginalMessageId] = rule {
    "Original-Message-ID" ~ ":" ~ capture(msgId) ~> ((msgId: String) => new OriginalMessageId(msgId))
  }

  //    msg-id          =   [CFWS] "<" id-left "@" id-right ">" [CFWS]
  private def msgId: Rule0 = rule { cfws.? ~ "<" ~ idLeft ~ "@" ~ idRight ~ ">" ~ cfws.? }

  //   id-left         =   dot-atom-text / obs-id-left
  private def idLeft: Rule0 = rule { dotAtomText | obsIdLeft }

  //   obs-id-left     =   local-part
  private def obsIdLeft: Rule0 = rule { localPart }

  //   obs-id-right    =   domain
  private def idRight = rule { domain }

  /*    disposition-field =
                     "Disposition" ":" OWS disposition-mode OWS ";"
                     OWS disposition-type
                     [ OWS "/" OWS disposition-modifier
                     *( OWS "," OWS disposition-modifier ) ] OWS    */
  private[mdn] def dispositionField : Rule1[Disposition] = rule {
    ("Disposition" ~ ":" ~ ows ~ dispositionMode ~ ows ~ ";" ~
    ows ~ dispositionType ~
    dispositionModifiers.? ~ ows) ~> ((modes: (DispositionActionMode, DispositionSendingMode),
                                                                              dispositionType: DispositionType,
                                                                              dispositionModifiers: Option[Seq[DispositionModifier]]) =>
       Disposition.builder()
         .actionMode(modes._1)
         .sendingMode(modes._2)
         .`type`(dispositionType)
         .addModifiers(dispositionModifiers.getOrElse(Nil):_*)
         .build()
      )
  }



  //    disposition-mode = action-mode OWS "/" OWS sending-mode
  private def dispositionMode: Rule1[(DispositionActionMode, DispositionSendingMode)] = rule {
    (capture(actionMode) ~ ows ~ "/" ~ ows ~ capture(sendingMode)) ~> ((actionMode: String, sendingMode: String) => {
      val action = actionMode match {
        case "manual-action" => DispositionActionMode.Manual
        case "automatic-action" => DispositionActionMode.Automatic
      }
      val sending = sendingMode match {
        case "MDN-sent-manually" => DispositionSendingMode.Manual
        case "MDN-sent-automatically" => DispositionSendingMode.Automatic
      }
      (action, sending)
    })
  }

  //    action-mode = "manual-action" / "automatic-action"
  private def actionMode = rule { "manual-action" | "automatic-action" }

  //    sending-mode = "MDN-sent-manually" / "MDN-sent-automatically"
  private def sendingMode = rule {"MDN-sent-manually" | "MDN-sent-automatically" }

  /*    disposition-type = "displayed" / "deleted" / "dispatched" /
                      "processed"    */
  private def dispositionType : Rule1[DispositionType] = rule {
    "displayed" ~ push(DispositionType.Displayed) |
    "deleted" ~ push(DispositionType.Deleted) |
    "dispatched" ~ push(DispositionType.Dispatched) |
    "processed" ~ push(DispositionType.Processed)
  }
  //subpart of disposition-field corresponding to :
  // [ OWS "/" OWS disposition-modifier
  //                     *( OWS "," OWS disposition-modifier ) ]
  private def dispositionModifiers: Rule1[Seq[DispositionModifier]] = rule { (ows ~ "/" ~ ows ~ capture(dispositionModifier) ~
      zeroOrMore(ows ~ "," ~ ows ~ capture(dispositionModifier))) ~> ((head: String, tail: Seq[String]) =>
      tail.prepended(head).map(new DispositionModifier(_)))
    }


  //    disposition-modifier = "error" / disposition-modifier-extension
  private def dispositionModifier = rule { "error" | dispositionModifierExtension }

  //    disposition-modifier-extension = Atom
  private def dispositionModifierExtension = rule { atom }

  //    error-field = "Error" ":" *([FWS] text)
  private[mdn] def errorField: Rule1[Error] = rule { ("Error" ~ ":" ~ capture(zeroOrMore(fws.? ~ text))) ~> ((error: String) =>  new Error(Text.fromRawText(error))) }

  //    extension-field = extension-field-name ":" *([FWS] text)
  private[mdn] def extentionField: Rule1[ExtensionField] = rule { capture(extensionFieldName) ~ ":" ~ capture(zeroOrMore(fws.? ~ text)) ~> ((extensionFieldName: String, text : String) =>
    ExtensionField.builder()
      .fieldName(extensionFieldName)
      .rawValue(text)
      .build()) }

  //    extension-field-name = field-name
  private def extensionFieldName: Rule0 = rule { fieldName }

  //   field-name      =   1*ftext
  private def fieldName: Rule0 = rule { oneOrMore(ftext) }

  /*   ftext           =   %d33-57 /          ; Printable US-ASCII
                         %d59-126           ;  characters not including
                                            ;  ":".   */
  private def ftext: Rule0 = rule {
    CharPredicate(33.toChar to 57.toChar) |
    CharPredicate(59.toChar to 126.toChar)
  }

  //   CFWS            =   (1*([FWS] comment) [FWS]) / FWS
  private def cfws: Rule0 = rule { (oneOrMore(fws.? ~ comment) ~ fws) | fws }

  //   FWS             =   ([*WSP CRLF] 1*WSP) /  obs-FWS
  private def fws: Rule0 = rule { ((zeroOrMore(wsp) ~ crlf).? ~ oneOrMore(wsp)) | obsFWS }

  //         WSP            =  SP / HTAB
  private def wsp: Rule0 = rule { sp | htab }

  //         SP             =  %x20
  private def sp: Rule0 = rule { ch(0x20) }

  //         HTAB           =  %x09
  private def htab: Rule0 = rule { ch(0x09) }

  //         CRLF           =  CR LF
  private def crlf: Rule0 = rule { cr ~ lf }

  //         CR             =  %x0D
  private def cr: Rule0 = rule { ch(0x0d) }

  //         LF             =  %x0A
  private def lf: Rule0 = rule { ch(0x0a) }

  //   obs-FWS         =   1*WSP *(CRLF 1*WSP)
  private def obsFWS: Rule0 = rule { oneOrMore(wsp) ~ zeroOrMore(crlf ~ oneOrMore(wsp)) }

  //   comment         =   "(" *([FWS] ccontent) [FWS] ")"
  private def comment: Rule[HNil, HNil] = rule { "(" ~ zeroOrMore(fws.? ~ ccontent) ~ fws.? ~ ")" }

  //   ccontent        =   ctext / quoted-pair / comment
  private def ccontent: Rule[HNil, HNil] = rule { ctext | quotedPair | comment }

  /*   ctext           =   %d33-39 /          ; Printable US-ASCII
                         %d42-91 /          ;  characters not including
                         %d93-126 /         ;  "(", ")", or "\"
                         obs-ctext   */
  private def ctext = rule {
    CharPredicate(33.toChar to 39.toChar) |
    CharPredicate(42.toChar to 91.toChar) |
    CharPredicate(93.toChar to 126.toChar) |
    obsCText
  }

  //   obs-ctext       =   obs-NO-WS-CTL
  private def obsCText = rule { obsNoWsCtl }

  /*   obs-NO-WS-CTL   =   %d1-8 /            ; US-ASCII control
                         %d11 /             ;  characters that do not
                         %d12 /             ;  include the carriage
                         %d14-31 /          ;  return, line feed, and
                         %d127              ;  white space characters   */
  private def obsNoWsCtl = rule {
    CharPredicate(33.toChar to 39.toChar) |
    ch(11) |
    ch(12) |
    CharPredicate(14.toChar to 31.toChar) |
    ch(127)
  }

  //   quoted-pair     =   ("\" (VCHAR / WSP)) / obs-qp
  private def quotedPair: Rule0 = rule { ("\\" ~ (vchar | wsp)) | obsQp }

  //         VCHAR          =  %x21-7E
  private def vchar: Rule0 = rule { CharPredicate(21.toChar to 0x7e.toChar) }

  //   obs-qp          =   "\" (%d0 / obs-NO-WS-CTL / LF / CR)
  private def obsQp: Rule0 = rule { "\\" ~ (ch(0xd0) | obsCText | lf | cr) }

  //   word            =   atom / quoted-string
  private def word: Rule0 = rule { atom | quotedString }

  //    atom            =   [CFWS] 1*atext [CFWS]
  private def atom: Rule0 = rule { cfws.? ~ oneOrMore(atext) ~ cfws.? }

  /*   atext           =   ALPHA / DIGIT /    ; Printable US-ASCII
                         "!" / "#" /        ;  characters not including
                         "$" / "%" /        ;  specials.  Used for atoms.
                         "&" / "'" /
                         "*" / "+" /
                         "-" / "/" /
                         "=" / "?" /
                         "^" / "_" /
                         "`" / "{" /
                         "|" / "}" /
                         "~"   */
  private def atext: Rule0 = rule {
    alpha | digit |
    "!" | "#" |
    "$" | "%" |
    "&" | "'" |
    "*" | "+" |
    "-" | "/" |
    "=" | "?" |
    "^" | "_" |
    "`" | "{" |
    "|" | "}" |
    "~"
  }

  //         ALPHA          =  %x41-5A / %x61-7A   ; A-Z / a-z
  private def alpha = rule {
    CharPredicate(0x41.toChar to 0x5a.toChar) |
    CharPredicate(0x61.toChar to 0x7a.toChar)
  }

  //         DIGIT          =  %x30-39
  private def digit = rule { CharPredicate(0x30.toChar to 0x39.toChar) }

  /*   quoted-string   =   [CFWS]
                                 DQUOTE *([FWS] qcontent) [FWS] DQUOTE
                                 [CFWS]   */

  private def quotedString: Rule0 = rule {
    cfws.? ~
    dquote ~ zeroOrMore(fws.? ~ qcontent) ~ fws.? ~ dquote ~
    cfws.?
  }

  //         DQUOTE         =  %x22
  private def dquote = rule { ch(0x22) }

  //   qcontent        =   qtext / quoted-pair
  private def qcontent: Rule0 = rule { qtext | quotedPair }

  //   qtext           =   %d33 /             ; Printable US-ASCII
  //                       %d35-91 /          ;  characters not including
  //                       %d93-126 /         ;  "\" or the quote character
  //                       obs-qtext
  private def qtext: Rule0 = rule {
    ch(33) |
    CharPredicate(35.toChar to 91.toChar) |
    CharPredicate(93.toChar to 126.toChar) |
    obsQtext
  }

  private def obsQtext: Rule0 = obsNoWsCtl

  //   domain          =   dot-atom / domain-literal / obs-domain
  private def domain = rule { dotAtom | domainLiteral | dotAtom }

  //   dot-atom        =   [CFWS] dot-atom-text [CFWS]
  private def dotAtom = rule { cfws.? ~ dotAtomText ~ cfws.? }

  //   dot-atom-text   =   1*atext *("." 1*atext)
  private def dotAtomText = rule { oneOrMore(atext) ~ zeroOrMore("." ~ oneOrMore(atext)) }

  //   domain-literal  =   [CFWS] "[" *([FWS] dtext) [FWS] "]" [CFWS]
  private def domainLiteral = rule {
    cfws.? ~ "[" ~ zeroOrMore(fws.? ~ dtext) ~ fws.? ~ "]" ~ cfws.?
  }

  /*   dtext           =   %d33-90 /          ; Printable US-ASCII
                                 %d94-126 /         ;  characters not including
                                 obs-dtext          ;  "[", "]", or "\"   */
  private def dtext = rule {
    CharPredicate(33.toChar to 90.toChar) |
    CharPredicate(94.toChar to 126.toChar) |
    obsDtext
  }

  //   obs-dtext       =   obs-NO-WS-CTL / quoted-pair
  private def obsDtext = rule { obsNoWsCtl | quotedPair }

  //   obs-domain      =   atom *("." atom)
  private def obsDomain = rule { atom ~ zeroOrMore("." ~ atom) }

  //   local-part      =   dot-atom / quoted-string / obs-local-part
  private def localPart: Rule0 = rule { dotAtom | quotedString | obsLocalPart }

  //   obs-local-part  =   word *("." word)
  private def obsLocalPart: Rule0 = rule { word ~ zeroOrMore("." ~ word) }

}