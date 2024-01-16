/****************************************************************
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

package org.apache.james.jmap.draft.methods.integration.cucumber;

import static org.apache.james.jmap.JMAPTestingConstants.ARGUMENTS;
import static org.apache.james.jmap.JMAPTestingConstants.NAME;
import static org.apache.james.jmap.JMAPTestingConstants.calmlyAwait;
import static org.apache.james.mailbox.model.MailboxConstants.INBOX;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.cucumber.datatable.DataTable;
import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.james.core.Username;
import org.apache.james.jmap.draft.JmapGuiceProbe;
import org.apache.james.jmap.draft.methods.integration.cucumber.util.TableRow;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.utils.SMTPMessageSender;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.minidev.json.JSONArray;

@ScenarioScoped
public class GetMessagesMethodStepdefs {

    private static final Optional<Map<String, String>> NO_HEADERS = Optional.empty();
    private static final String FIRST_MESSAGE = ARGUMENTS + ".list[0]";
    private static final String ATTACHMENTS = FIRST_MESSAGE + ".attachments";
    private static final String FIRST_ATTACHMENT = ATTACHMENTS + "[0]";
    private static final String SECOND_ATTACHMENT = ATTACHMENTS + "[1]";
    private static final int PREVIEW_LENGTH = 256;


    private final MainStepdefs mainStepdefs;
    private final UserStepdefs userStepdefs;
    private final HttpClient httpClient;
    private final MessageIdStepdefs messageIdStepdefs;

    private List<MessageId> requestedMessageIds;

    @Inject
    private GetMessagesMethodStepdefs(MainStepdefs mainStepdefs, UserStepdefs userStepdefs,
                                      HttpClient httpClient, MessageIdStepdefs messageIdStepdefs) {
        this.mainStepdefs = mainStepdefs;
        this.userStepdefs = userStepdefs;
        this.httpClient = httpClient;
        this.messageIdStepdefs = messageIdStepdefs;
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" and \"([^\"]*)\" mailboxes with subject \"([^\"]*)\", content \"([^\"]*)\"$")
    public void appendMessageInTwoMailboxes(String messageName, String mailbox1, String mailbox2, String subject, String content) throws Exception {
        MessageId id = appendMessage(mailbox1, ContentType.noContentType(), subject, content, NO_HEADERS);

        String user = userStepdefs.getConnectedUser();

        MailboxId mailboxId1 = mainStepdefs.getMailboxId(user, mailbox1);
        MailboxId mailboxId2 = mainStepdefs.getMailboxId(user, mailbox2);

        mainStepdefs.jmapServer.getProbe(JmapGuiceProbe.class).setInMailboxes(id, Username.of(user), mailboxId1, mailboxId2);
        messageIdStepdefs.addMessageId(messageName, id);
        mainStepdefs.awaitMethod.run();
    }

    @Given("^\"([^\"]*)\" has a message \"([^\"]*)\" in \"([^\"]*)\" and \"([^\"]*)\" mailboxes with subject \"([^\"]*)\", content \"([^\"]*)\"$")
    public void appendMessageInTwoMailboxes(String username, String messageName, String mailbox1, String mailbox2, String subject, String content) throws Throwable {
        userStepdefs.execWithUser(username, () -> appendMessageInTwoMailboxes(messageName, mailbox1, mailbox2, subject, content));
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with subject \"([^\"]*)\", content \"([^\"]*)\"$")
    public void appendMessage(String messageName, String mailbox, String subject, String content) throws Exception {
        MessageId id = appendMessage(mailbox, ContentType.noContentType(), subject, content, NO_HEADERS);
        messageIdStepdefs.addMessageId(messageName, id);
    }

    @Given("^\"([^\"]*)\" has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with subject \"([^\"]*)\", content \"([^\"]*)\"$")
    public void appendMessage(String username, String messageName, String mailbox, String subject, String content) throws Throwable {
        userStepdefs.execWithUser(username, () -> appendMessage(messageName, mailbox, subject, content));
    }

    @Given("^\"([^\"]*)\" has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with content-type \"([^\"]*)\" subject \"([^\"]*)\", content \"([^\"]*)\"$")
    public void appendMessageWithContentType(String username, String messageName, String mailbox, String contentType, String subject, String content) throws Throwable {
        userStepdefs.execWithUser(username, () -> appendMessageWithContentType(messageName, mailbox, contentType, subject, content));
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with content-type \"([^\"]*)\" subject \"([^\"]*)\", content \"([^\"]*)\"$")
    public void appendMessageWithContentType(String messageName, String mailbox, String contentType, String subject, String content) throws Throwable {
        MessageId id = appendMessage(mailbox, ContentType.from(contentType), subject, content, NO_HEADERS);
        messageIdStepdefs.addMessageId(messageName, id);
    }

    @Given("^\"([^\"]*)\" has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with content-type \"([^\"]*)\" subject \"([^\"]*)\", content \"([^\"]*)\", headers$")
    public void appendMessage(String username, String messageName, String mailbox, String contentType, String subject, String content, DataTable headers) throws Throwable {
        userStepdefs.execWithUser(username, () -> appendMessage(messageName, mailbox, contentType, subject, content, headers));
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with content-type \"([^\"]*)\" subject \"([^\"]*)\", content \"([^\"]*)\", headers$")
    public void appendMessage(String messageName, String mailbox, String contentType, String subject, String content, DataTable headers) throws Exception {
        MessageId id = appendMessage(mailbox, ContentType.from(contentType), subject, content, Optional.of(headers.asMap(String.class, String.class)));
        messageIdStepdefs.addMessageId(messageName, id);
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with subject \"([^\"]*)\", content \"([^\"]*)\", headers$")
    public void appendMessageWithHeader(String messageName, String mailbox, String subject, String content, DataTable headers) throws Exception {
        MessageId id = appendMessage(mailbox, ContentType.noContentType(), subject, content, Optional.of(headers.asMap(String.class, String.class)));
        messageIdStepdefs.addMessageId(messageName, id);
    }

    @Given("^\"([^\"]*)\" has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with subject \"([^\"]*)\", content \"([^\"]*)\", headers$")
    public void appendMessageWithHeader(String username, String messageName, String mailbox, String subject, String content, DataTable headers) throws Throwable {
        userStepdefs.execWithUser(username, () -> appendMessageWithHeader(messageName, mailbox, subject, content, headers));
    }

    @Given("^\"([^\"]*)\" has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox, composed of a multipart with inlined text part and inlined html part$")
    public void appendMessageFromFileInlinedMultipart(String username, String messageName, String mailbox) throws Throwable {
        userStepdefs.execWithUser(username, () -> appendMessageFromFileInlinedMultipart(messageName, mailbox));
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox, composed of a multipart with inlined text part and inlined html part$")
    public void appendMessageFromFileInlinedMultipart(String messageName, String mailbox) throws Exception {
        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        MessageId id = mainStepdefs.mailboxProbe.appendMessage(userStepdefs.getConnectedUser(),
            MailboxPath.forUser(Username.of(userStepdefs.getConnectedUser()), mailbox),
            ClassLoader.getSystemResourceAsStream("eml/inlinedMultipart.eml"),
            Date.from(dateTime.toInstant()), false, new Flags())
            .getMessageId();
        messageIdStepdefs.addMessageId(messageName, id);
        mainStepdefs.awaitMethod.run();
    }

    private MessageId appendMessage(String mailbox, ContentType contentType, String subject, String content, Optional<Map<String, String>> headers) throws Exception {
        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        try {
            return mainStepdefs.mailboxProbe.appendMessage(userStepdefs.getConnectedUser(),
                MailboxPath.forUser(Username.of(userStepdefs.getConnectedUser()), mailbox),
                new ByteArrayInputStream(message(contentType, subject, content, headers).getBytes(StandardCharsets.UTF_8)),
                Date.from(dateTime.toInstant()), false, new Flags()).getMessageId();
        } finally {
            mainStepdefs.awaitMethod.run();
        }
    }

    private String message(ContentType contentType, String subject, String content, Optional<Map<String,String>> headers) {
        return serialize(headers) + contentType.serializeToHeader() + "Subject: " + subject + "\r\n\r\n" + content;
    }

    private String serialize(Optional<Map<String,String>> headers) {
        return headers
            .map(Map::entrySet)
            .map(entriesToString())
            .orElse("");
    }

    private Function<Set<Entry<String, String>>, String> entriesToString() {
        return entries -> entries.stream()
            .map(entry -> entry.getKey() + ": " + entry.getValue())
            .collect(Collectors.joining("\r\n", "", "\r\n"));
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with two attachments$")
    public void appendHtmlMessageWithTwoAttachments(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, mailbox, "eml/twoAttachments.eml");
    }

    @Given("^\"([^\"]*)\" has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with two attachments$")
    public void appendHtmlMessageWithTwoAttachments(String username, String messageName, String mailbox) throws Throwable {
        userStepdefs.execWithUser(username, () -> appendHtmlMessageWithTwoAttachments(messageName, mailbox));
    }

    @Given("^\"([^\"]*)\" has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with two attachments in text$")
    public void appendTextMessageWithTwoAttachments(String username, String messageName, String mailbox) throws Throwable {
        userStepdefs.execWithUser(username, () -> appendTextMessageWithTwoAttachments(messageName, mailbox));
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with two attachments in text$")
    public void appendTextMessageWithTwoAttachments(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, mailbox, "eml/twoAttachmentsTextPlain.eml");
    }

    @Given("^\"([^\"]*)\" has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with two same attachments in text$")
    public void appendTextMessageWithTwoSameAttachments(String username, String messageName, String mailbox) throws Throwable {
        userStepdefs.execWithUser(username, () -> appendTextMessageWithTwoAttachments(messageName, mailbox));
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with two same attachments in text$")
    public void appendTextMessageWithTwoSameAttachments(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, mailbox, "eml/twoSameAttachments.eml");
    }

    @Given("^\"([^\"]*)\" has a multipart message \"([^\"]*)\" in \"([^\"]*)\" mailbox$")
    public void appendMultipartMessageWithOneAttachments(String username, String messageName, String mailbox) throws Throwable {
        userStepdefs.execWithUser(username, () -> appendMultipartMessageWithOneAttachments(messageName, mailbox));
    }

    @Given("^the user has a multipart message \"([^\"]*)\" in \"([^\"]*)\" mailbox$")
    public void appendMultipartMessageWithOneAttachments(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, mailbox, "eml/htmlAndTextMultipartWithOneAttachment.eml");
    }

    @Given("\"([^\"]*)\" has a multipart/related message \"([^\"]*)\" in \"([^\"]*)\" mailbox$")
    public void appendMultipartRelated(String username, String messageName, String mailbox) throws Throwable {
        userStepdefs.execWithUser(username, () -> appendMultipartRelated(messageName, mailbox));
    }

    @Given("^the user has a multipart/related message \"([^\"]*)\" in \"([^\"]*)\" mailbox$")
    public void appendMultipartRelated(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, mailbox, "eml/multipartRelated.eml");
    }

    @Given("^\"([^\"]*)\" has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox beginning by a long line$")
    public void appendMessageBeginningByALongLine(String username, String messageName, String mailbox) throws Throwable {
        userStepdefs.execWithUser(username, () -> appendMessageBeginningByALongLine(messageName, mailbox));
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox beginning by a long line$")
    public void appendMessageBeginningByALongLine(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, mailbox, "eml/longLine.eml");
    }

    @Given("^\"([^\"]*)\" has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with plain/text inline attachment$")
    public void appendMessageWithPlainTextInlineAttachment(String username, String messageName, String mailbox) throws Throwable {
        userStepdefs.execWithUser(username, () -> appendMessageWithPlainTextInlineAttachment(messageName, mailbox));
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with plain/text inline attachment$")
    public void appendMessageWithPlainTextInlineAttachment(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, mailbox, "eml/embeddedMultipartWithInlineTextAttachment.eml");
    }


    @Given("^\"([^\"]*)\" has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with text in main multipart and html in inner multipart$")
    public void appendMessageWithTextInMainMultipartAndHtmlInInnerMultipart(String username, String messageName, String mailbox) throws Throwable {
        userStepdefs.execWithUser(username, () -> appendMessageWithTextInMainMultipartAndHtmlInInnerMultipart(messageName, mailbox));
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with text in main multipart and html in inner multipart$")
    public void appendMessageWithTextInMainMultipartAndHtmlInInnerMultipart(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, mailbox, "eml/textInMainMultipartHtmlInInnerMultipart.eml");
    }

    @Given("^\"([^\"]*)\" has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with html body and no text body$")
    public void appendMessageWithNoTextButHtml(String username, String messageName, String mailbox) throws Throwable {
        userStepdefs.execWithUser(username, () -> appendMessageWithNoTextButHtml(messageName, mailbox));
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with html body and no text body$")
    public void appendMessageWithNoTextButHtml(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, mailbox, "eml/noTextBodyButHtmlBody.eml");
    }

    @Given("^\"([^\"]*)\" has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with inline attachment but no CID$")
    public void appendMessageWithInlineAttachmentButNoCid(String username, String messageName, String mailbox) throws Throwable {
        userStepdefs.execWithUser(username, () -> appendMessageWithInlineAttachmentButNoCid(messageName, mailbox));
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with inline attachment but no CID$")
    public void appendMessageWithInlineAttachmentButNoCid(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, mailbox, "eml/mailWithInlinedAttachmentButNoCid.eml");
    }

    @Given("^\"([^\"]*)\" has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with inline attachment and blank CID$")
    public void appendMessageWithInlineAttachmentAndBlankCid(String username, String messageName, String mailbox) throws Throwable {
        userStepdefs.execWithUser(username, () -> appendMessageWithInlineAttachmentAndBlankCid(messageName, mailbox));
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with inline attachment and blank CID$")
    public void appendMessageWithInlineAttachmentAndBlankCid(String messageName, String mailbox) throws Throwable {
        appendMessage(messageName, mailbox, "eml/mailWithInlinedAttachmentAndBlankCid.eml");
    }

    @Given("^\"([^\"]*)\" has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with HTML body with many empty tags$")
    public void appendMessageWithNoPreview(String username, String messageName, String mailbox) throws Throwable {
        userStepdefs.execWithUser(username, () -> appendMessageWithNoPreview(messageName, mailbox));
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with HTML body with many empty tags$")
    public void appendMessageWithNoPreview(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, mailbox, "eml/htmlBodyWithManyEmptyTags.eml");
    }

    @Given("^\"([^\"]*)\" has a message \"([^\"]*)\" in the \"([^\"]*)\" mailbox with multiple same inlined attachments \"(?:[^\"]*)\"$")
    public void appendMessageWithSameInlinedAttachmentsToMailbox(String username, String messageName, String mailbox) throws Throwable {
        userStepdefs.execWithUser(username, () -> appendMessageWithSameInlinedAttachmentsToMailbox(messageName, mailbox));
    }

    @Given("^the user has a message \"([^\"]*)\" in the \"([^\"]*)\" mailbox with multiple same inlined attachments \"(?:[^\"]*)\"$")
    public void appendMessageWithSameInlinedAttachmentsToMailbox(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, mailbox, "eml/sameInlinedImages.eml");
    }

    @Given("^\"([^\"]*)\" has a message \"([^\"]*)\" in the \"([^\"]*)\" mailbox with inlined attachments without content disposition$")
    public void appendMessageWithInlinedAttachmentButNoContentDisposition(String username, String messageName, String mailbox) throws Exception {
        userStepdefs.execWithUser(username, () -> appendMessage(messageName, mailbox, "eml/inlinedWithoutContentDisposition.eml"));
    }

    @Given("^\"([^\"]*)\" has a message \"([^\"]*)\" in the \"([^\"]*)\" mailbox with inlined image without content disposition$")
    public void appendMessageWithInlinedImageButNoContentDisposition(String username, String messageName, String mailbox) throws Exception {
        userStepdefs.execWithUser(username, () -> appendMessage(messageName, mailbox, "eml/oneInlinedImageWithoutContentDisposition.eml"));
    }

    @Given("^\"([^\"]*)\" has a message \"([^\"]*)\" in the \"([^\"]*)\" mailbox with inlined image without content ID$")
    public void appendMessageWithInlinedImageButNoContentID(String username, String messageName, String mailbox) throws Exception {
        userStepdefs.execWithUser(username, () -> appendMessage(messageName, mailbox, "eml/inlinedWithoutContentID.eml"));
    }

    @Given("^\"([^\"]*)\" has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with specific charset$")
    public void appendMessageWithSpecificCharset(String username, String messageName, String mailbox) throws Throwable {
        userStepdefs.execWithUser(username, () -> appendMessageWithSpecificCharset(messageName, mailbox));
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with specific charset$")
    public void appendMessageWithSpecificCharset(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, mailbox, "eml/windows1252charset.eml");
    }

    @Given("^\"([^\"]*)\" has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with long and complicated HTML content$")
    public void appendMessageWithSpecialCase(String username, String messageName, String mailbox) throws Throwable {
        userStepdefs.execWithUser(username, () -> appendMessageWithSpecialCase(messageName, mailbox));
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with long and complicated HTML content$")
    public void appendMessageWithSpecialCase(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, mailbox, "eml/htmlWithLongAndComplicatedContent.eml");
    }

    @Given("^\"([^\"]*)\" has a message \"([^\"]*)\" in the \"([^\"]*)\" mailbox with flags \"([^\"]*)\"$")
    public void appendMessageWithFlags(String username, String messageName, String mailbox, String flagList) throws Throwable {
        userStepdefs.execWithUser(username, () -> appendMessageWithFlags(messageName, mailbox, flagList));
    }

    @Given("^\"([^\"]*)\" has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox$")
    public void appendSimpleMessage(String username, String messageName, String mailbox) throws Throwable {
        userStepdefs.execWithUser(username, () -> appendMessageWithFlags(messageName, mailbox, ""));
    }

    @Given("^the user has a message \"([^\"]*)\" in the \"([^\"]*)\" mailbox with flags \"([^\"]*)\"$")
    public void appendMessageWithFlags(String messageName, String mailbox, String flagList) throws Exception {
        appendMessage(messageName, mailbox, StringListToFlags.fromFlagList(Splitter.on(',').trimResults().omitEmptyStrings().splitToList(flagList)));
    }

    @Given("^\"([^\"]*)\" receives a SMTP message specified in file \"([^\"]*)\" as message \"([^\"]*)\"$")
    public void smtpSend(String user, String fileName, String messageName) throws Exception {
        MailboxId mailboxId = mainStepdefs.mailboxProbe.getMailboxId(MailboxConstants.USER_NAMESPACE, user, INBOX);
        SMTPMessageSender smtpMessageSender = new SMTPMessageSender("domain.com");
        smtpMessageSender
            .connect("127.0.0.1", mainStepdefs.jmapServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .sendMessageWithHeaders("from@domain.com", user,
                ClassLoaderUtils.getSystemResourceAsString(fileName));
        smtpMessageSender.close();

        calmlyAwait.until(() -> !retrieveIds(user, mailboxId).isEmpty());
        List<String> ids = retrieveIds(user, mailboxId);
        messageIdStepdefs.addMessageId(messageName, mainStepdefs.messageIdFactory.fromString(ids.get(0)));
    }

    public List<String> retrieveIds(String user, MailboxId mailboxId) {
        userStepdefs.execWithUser(user, () -> httpClient.post("[[\"getMessageList\", {\"filter\":{\"inMailboxes\":[\"" + mailboxId.serialize() + "\"]}}, \"#0\"]]"));
        return httpClient.jsonPath.read(ARGUMENTS + ".messageIds.[*]");
    }

    private void appendMessage(String messageName, String mailbox, Flags flags) throws Exception {
        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        boolean isRecent = flags.contains(Flags.Flag.RECENT);
        MessageId id = mainStepdefs.mailboxProbe.appendMessage(userStepdefs.getConnectedUser(),
            MailboxPath.forUser(Username.of(userStepdefs.getConnectedUser()), mailbox),
            new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()),
            Date.from(dateTime.toInstant()), isRecent, flags)
            .getMessageId();
        messageIdStepdefs.addMessageId(messageName, id);
        mainStepdefs.awaitMethod.run();
    }

    private void appendMessage(String messageName, String mailbox, String emlFileName) throws Exception {
        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");

        MessageId id = mainStepdefs.mailboxProbe.appendMessage(userStepdefs.getConnectedUser(),
            MailboxPath.forUser(Username.of(userStepdefs.getConnectedUser()), mailbox),
                ClassLoader.getSystemResourceAsStream(emlFileName),
                Date.from(dateTime.toInstant()), false, new Flags())
                    .getMessageId();

        messageIdStepdefs.addMessageId(messageName, id);
        mainStepdefs.awaitMethod.run();
    }

    @When("^\"([^\"]*)\" ask for messages using its accountId$")
    public void postWithAccountId(String user) throws Throwable {
        userStepdefs.execWithUser(user, this::postWithAccountId);
    }

    @When("^\the user ask for messages using its accountId$")
    public void postWithAccountId() throws Exception {
        httpClient.post("[[\"getMessages\", {\"accountId\": \"1\"}, \"#0\"]]");
    }

    @When("^\"([^\"]*)\" ask for messages using unknown arguments$")
    public void postWithUnknownArguments(String user) throws Throwable {
        userStepdefs.execWithUser(user, this::postWithUnknownArguments);
    }

    @When("^the user ask for messages using unknown arguments$")
    public void postWithUnknownArguments() throws Exception {
        httpClient.post("[[\"getMessages\", {\"WAT\": true}, \"#0\"]]");
    }

    @When("^the user ask for messages using invalid argument$")
    public void postWithInvalidArguments() throws Exception {
        httpClient.post("[[\"getMessages\", {\"ids\": null}, \"#0\"]]");
    }

    @When("^\"([^\"]*)\" ask for messages using invalid argument$")
    public void postWithInvalidArguments(String user) throws Throwable {
        userStepdefs.execWithUser(user, this::postWithInvalidArguments);
    }

    @When("^the user ask for messages$")
    public void post() throws Exception {
        httpClient.post("[[\"getMessages\", {\"ids\": []}, \"#0\"]]");
    }

    @When("^\"(.*?)\" ask for messages$")
    public void postWithGivenUser(String username) throws Throwable {
        userStepdefs.execWithUser(username, this::post);
    }

    @When("^the user ask for messages \"(.*?)\"$")
    public void postWithAListOfIds(String id) throws Exception {
        requestedMessageIds = ImmutableList.of(messageIdStepdefs.getMessageId(id));
        askMessages(requestedMessageIds);
    }

    @When("^the user ask for message \"(.*?)\"$")
    public void postWithAnId(String ids) throws Exception {
        requestedMessageIds = Splitter.on(',').trimResults().splitToStream(ids)
            .map(messageIdStepdefs::getMessageId)
            .collect(ImmutableList.toImmutableList());
        askMessages(requestedMessageIds);
    }

    @When("^\"(.*?)\" ask for messages \"(.*?)\"$")
    public void postWithAListOfIds(String user, String ids) {
        userStepdefs.execWithUser(user, () -> postWithAListOfIds(ids));
    }

    @When("^\"(.*?)\" ask for message \"(.*?)\"$")
    public void postWithAnId(String user, String id) {
        userStepdefs.execWithUser(user, () -> postWithAListOfIds(id));
    }

    @When("^\"(.*?)\" ask for an unknown message$")
    public void requestUnknownMessage(String user) throws Throwable {
        userStepdefs.execWithUser(user, this::requestUnknownMessage);
    }

    @When("^the user ask for an unknown message$")
    public void requestUnknownMessage() throws Exception {
        askMessages(ImmutableList.of(mainStepdefs.messageIdFactory.generate()));
    }

    private void askMessages(List<MessageId> messageIds) throws Exception {
        requestedMessageIds = messageIds;
        String serializedIds = requestedMessageIds.stream()
            .map(MessageId::serialize)
            .map(toJsonString())
            .collect(Collectors.joining(",", "[", "]"));
        httpClient.post("[[\"getMessages\", {\"ids\": " + serializedIds + "}, \"#0\"]]");
    }

    private Function<? super String, ? extends String> toJsonString() {
        return string -> "\"" + string + "\"";
    }

    @When("^\"(.*?)\" is getting message \"(.*?)\" with properties \"(.*?)\"$")
    public void postWithParameters(String username, String id, String properties) throws Throwable {
        userStepdefs.execWithUser(username, () -> postWithParameters(id, properties));
    }

    @When("^the user is getting messages \"(.*?)\" with properties \"(.*?)\"$")
    public void postWithParameters(String ids, String properties) throws Exception {
        requestedMessageIds = Splitter.on(',').trimResults().splitToStream(ids)
            .map(messageIdStepdefs::getMessageId)
            .collect(ImmutableList.toImmutableList());

        String serializedIds = requestedMessageIds.stream()
            .map(MessageId::serialize)
            .map(toJsonString())
            .collect(Collectors.joining(",", "[", "]"));

        String serializedProperties = Splitter.on(',').trimResults().splitToStream(properties)
            .map(toJsonString())
            .collect(Collectors.joining(",", "[", "]"));

        httpClient.post("[[\"getMessages\", {\"ids\": " + serializedIds + ", \"properties\": " + serializedProperties + "}, \"#0\"]]");
    }

    @Then("^an error \"([^\"]*)\" with type \"([^\"]*)\" is returned$")
    public void error(String description, String type) {
        assertThat(httpClient.response.getStatusLine().getStatusCode()).isEqualTo(200);
        assertThat(httpClient.jsonPath.<String>read(NAME)).isEqualTo("error");
        assertThat(httpClient.jsonPath.<String>read(ARGUMENTS + ".type")).isEqualTo(type);
        assertThat(httpClient.jsonPath.<String>read(ARGUMENTS + ".description")).isEqualTo(description);
    }

    @Then("^an error of type \"([^\"]*)\" is returned$")
    public void errorType(String type) {
        assertThat(httpClient.response.getStatusLine().getStatusCode()).isEqualTo(200);
        assertThat(httpClient.jsonPath.<String>read(NAME)).isEqualTo("error");
        assertThat(httpClient.jsonPath.<String>read(ARGUMENTS + ".type")).isEqualTo(type);
    }

    @Then("^no error is returned$")
    public void noError() {
        assertThat(httpClient.response.getStatusLine().getStatusCode()).isEqualTo(200);
        assertThat(httpClient.jsonPath.<String>read(NAME)).isEqualTo("messages");
    }

    @Then("^the list of unknown messages is empty$")
    public void assertNotFoundIsEmpty() {
        assertThat(httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".notFound")).isEmpty();
    }

    @Then("^the list of messages is empty$")
    public void assertListIsEmpty() {
        assertThat(httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".list")).isEmpty();
    }

    @Then("^the notFound list should contain \"([^\"]*)\"$")
    public void assertNotFoundListContains(String id) {
        MessageId messageId = messageIdStepdefs.getMessageId(id);
        assertThat(httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".notFound")).contains(messageId.serialize());
    }

    @Then("^the notFound list should contain the requested message id$")
    public void assertNotFoundListContainsRequestedMessages() {
        ImmutableList<String> elements = requestedMessageIds.stream().map(MessageId::serialize).collect(ImmutableList.toImmutableList());
        assertThat(httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".notFound"))
            .containsExactlyElementsOf(elements);
    }


    @Then("^the list should contain (\\d+) message$")
    public void assertListContains(int numberOfMessages) {
        assertThat(httpClient.jsonPath.<List<String>>read(ARGUMENTS + ".list")).hasSize(numberOfMessages);
    }

    @Then("^the id of the message is \"([^\"]*)\"$")
    public void assertIdOfTheFirstMessage(String messageName) {
        MessageId id = messageIdStepdefs.getMessageId(messageName);
        assertThat(httpClient.jsonPath.<String>read(FIRST_MESSAGE + ".id")).isEqualTo(id.serialize());
    }

    @Then("^the message is in \"([^\"]*)\" mailboxes")
    public void assertMailboxNamesOfTheFirstMessage(String mailboxNames) {
        List<String> mailboxIds = mainStepdefs.getMailboxIdsList(userStepdefs.getConnectedUser(),
            Splitter.on(",").splitToList(mailboxNames));

        assertThat(httpClient.jsonPath.<JSONArray>read(FIRST_MESSAGE + ".mailboxIds"))
            .containsExactlyInAnyOrder(mailboxIds.toArray());
    }

    @Then("^\"([^\"]*)\" should see message \"([^\"]*)\" in mailboxes:$")
    public void assertMailboxesOfMessage(String user, String messageId, DataTable userMailboxes) throws Exception {
        userStepdefs.execWithUser(user, () -> postWithAListOfIds(messageId));

        List<String> mailboxIds = userMailboxes.asMap(String.class, String.class).entrySet().stream()
            .map(Throwing.function(userMailbox ->
                mainStepdefs
                    .getMailboxId(userMailbox.getKey(), userMailbox.getValue())
                    .serialize()))
            .distinct()
            .collect(ImmutableList.toImmutableList());

        assertThat(httpClient.jsonPath.<JSONArray>read(FIRST_MESSAGE + ".mailboxIds"))
            .containsExactlyInAnyOrder(mailboxIds.toArray());
    }

    @Then("^the threadId of the message is \"([^\"]*)\"$")
    public void assertThreadIdOfTheFirstMessage(String threadId) {
        MessageId id = messageIdStepdefs.getMessageId(threadId);
        assertThat(httpClient.jsonPath.<String>read(FIRST_MESSAGE + ".threadId")).isEqualTo(id.serialize());
    }

    @Then("^the subject of the message is \"([^\"]*)\"$")
    public void assertSubjectOfTheFirstMessage(String subject) {
        assertThat(httpClient.jsonPath.<String>read(FIRST_MESSAGE + ".subject")).isEqualTo(subject);
    }

    @Then("^the textBody of the message is \"([^\"]*)\"$")
    public void assertTextBodyOfTheFirstMessage(String textBody) {
        assertThat(httpClient.jsonPath.<String>read(FIRST_MESSAGE + ".textBody")).isEqualTo(StringEscapeUtils.unescapeJava(textBody));
    }

    @Then("^the htmlBody of the message is \"([^\"]*)\"$")
    public void assertHtmlBodyOfTheFirstMessage(String htmlBody) {
        assertThat(httpClient.jsonPath.<String>read(FIRST_MESSAGE + ".htmlBody")).isEqualTo(StringEscapeUtils.unescapeJava(htmlBody));
    }

    @Then("^the isUnread of the message is \"([^\"]*)\"$")
    public void assertIsUnreadOfTheFirstMessage(String isUnread) {
        assertThat(httpClient.jsonPath.<Boolean>read(FIRST_MESSAGE + ".isUnread")).isEqualTo(Boolean.valueOf(isUnread));
    }

    @Then("^the preview of the message is \"([^\"]*)\"$")
    public void assertPreviewOfTheFirstMessage(String preview) {
        String actual = httpClient.jsonPath.<String>read(FIRST_MESSAGE + ".preview").replace("\n", " ");
        assertThat(actual).isEqualToIgnoringWhitespace(StringEscapeUtils.unescapeJava(preview));
    }

    @Then("^the preview of the message is not empty$")
    public void assertPreviewOfTheFirstMessageIsNotEmpty() {
        String actual = httpClient.jsonPath.read(FIRST_MESSAGE + ".preview");
        assertThat(actual).isNotEmpty();
    }

    @Then("^the preview should not contain consecutive spaces or blank characters$")
    public void assertPreviewShouldBeNormalized() {
        String actual = httpClient.jsonPath.read(FIRST_MESSAGE + ".preview");
        assertThat(actual).hasSize(PREVIEW_LENGTH)
            .doesNotMatch("  ")
            .doesNotContain(StringUtils.CR)
            .doesNotContain(StringUtils.LF);
    }

    @Then("^the headers of the message contains:$")
    public void assertHeadersOfTheFirstMessage(DataTable headers) {
        assertThat(httpClient.jsonPath.<Map<String, String>>read(FIRST_MESSAGE + ".headers")).containsAllEntriesOf(headers.asMap(String.class, String.class));
    }

    @Then("^the date of the message is \"([^\"]*)\"$")
    public void assertDateOfTheFirstMessage(String date) {
        assertThat(httpClient.jsonPath.<String>read(FIRST_MESSAGE + ".date")).isEqualTo(date);
    }

    @Then("^the hasAttachment of the message is \"([^\"]*)\"$")
    public void assertHasAttachmentOfTheFirstMessage(String hasAttachment) {
        assertThat(httpClient.jsonPath.<Boolean>read(FIRST_MESSAGE + ".hasAttachment")).isEqualTo(Boolean.valueOf(hasAttachment));
    }

    @Then("^the isForwarded property of the message is \"([^\"]*)\"$")
    public void assertIsForwardedOfTheFirstMessage(String isForwarded) {
        assertThat(httpClient.jsonPath.<Boolean>read(FIRST_MESSAGE + ".isForwarded")).isEqualTo(Boolean.valueOf(isForwarded));
    }

    @Then("^the list of attachments of the message is empty$")
    public void assertAttachmentsOfTheFirstMessageIsEmpty() {
        assertThat(httpClient.jsonPath.<List<Object>>read(ATTACHMENTS)).isEmpty();
    }

    @Then("^the property \"([^\"]*)\" of the message is null$")
    public void assertPropertyIsNull(String property) {
        assertThat(httpClient.jsonPath.<String>read(FIRST_MESSAGE + "." + property + ".date")).isNull();
    }

    @Then("^the list of attachments of the message contains (\\d+) attachments?$")
    public void assertAttachmentsHasSize(int numberOfAttachments) {
        assertThat(httpClient.jsonPath.<List<Object>>read(ATTACHMENTS)).hasSize(numberOfAttachments);
    }

    @Then("^the list of attachments of the message contains only one attachment with cid \"([^\"]*)\"?$")
    public void assertAttachmentsAndItsCid(String cid) {
        assertThat(httpClient.jsonPath.<String>read(FIRST_ATTACHMENT + ".cid")).isEqualTo(cid);
    }

    @Then("^the first attachment is:$")
    public void assertFirstAttachment(DataTable attachmentProperties) {
        assertAttachment(FIRST_ATTACHMENT, attachmentProperties);
    }

    @Then("^the second attachment is:$")
    public void assertSecondAttachment(DataTable attachmentProperties) {
        assertAttachment(SECOND_ATTACHMENT, attachmentProperties);
    }

    @Then("^the preview of the message contains: \"(.*)\"$")
    public void assertPreviewOfMessageShouldBePrintedWithEncoding(String preview) {
        String actual = httpClient.jsonPath.<String>read(FIRST_MESSAGE + ".preview");
        assertThat(actual).contains(preview);
    }

    @Then("^the keywords of the message is (.*)$")
    public void assertKeywordsOfMessageShouldDisplay(String keywords) {
        assertThat(httpClient.jsonPath.<Map<String, Boolean>>read(FIRST_MESSAGE + ".keywords").keySet())
            .containsOnly(Splitter.on(',').trimResults().omitEmptyStrings().splitToList(keywords).toArray(new String[0]));
    }

    @Then("^the message has no keyword$")
    public void assertMessageHasNoKeyword() {
        assertThat(httpClient.jsonPath.<Map<String, Boolean>>read(FIRST_MESSAGE + ".keywords"))
            .isNullOrEmpty();
    }

    @Then("^\"([^\"]*)\" should see message \"([^\"]*)\" with keywords \"([^\"]*)\"$")
    public void assertKeywordsOfMessage(String user, String messageId, String keywords) throws Exception {
        userStepdefs.execWithUser(user, () -> postWithAListOfIds(messageId));

        assertThat(httpClient.jsonPath.<Map<String, Boolean>>read(FIRST_MESSAGE + ".keywords").keySet())
            .containsOnly(Splitter.on(',').trimResults().splitToList(keywords).toArray(new String[0]));
    }

    @Then("^\"([^\"]*)\" should see message \"([^\"]*)\" without keywords$")
    public void assertKeywordsEmpty(String user, String messageId) throws Exception {
        userStepdefs.execWithUser(user, () -> postWithAListOfIds(messageId));

        assertThat(httpClient.jsonPath.<Map<String, Boolean>>read(FIRST_MESSAGE + ".keywords").keySet())
            .isEmpty();
    }

    private void assertAttachment(String attachment, DataTable attachmentProperties) {
        try {
            System.out.println(IOUtils.toString(httpClient.response.getEntity().getContent()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        attachmentProperties.asMap(String.class, String.class)
            .forEach((key, value) -> {
                System.out.println(key + " : " + value);
                System.out.println(httpClient.jsonPath.<Object>read(attachment + "." + key));
                assertThat(String.valueOf(httpClient.jsonPath.<Object>read(attachment + "." + key))).isEqualTo(value);
            });
    }

    public String getBlobId() {
        return httpClient.jsonPath.<String>read(FIRST_MESSAGE + ".blobId");
    }
}
