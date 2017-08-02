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

package org.apache.james.jmap.methods.integration.cucumber;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.james.jmap.DefaultMailboxes;
import org.apache.james.jmap.methods.integration.cucumber.util.TableRow;
import org.apache.james.jmap.model.MessagePreviewGenerator;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.utils.JmapGuiceProbe;
import org.javatuples.Pair;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

import cucumber.api.DataTable;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.runtime.java.guice.ScenarioScoped;
import net.minidev.json.JSONArray;

@ScenarioScoped
public class GetMessagesMethodStepdefs {

    private static final Optional<Map<String, String>> NO_HEADERS = Optional.empty();
    private static final String NAME = "[0][0]";
    private static final String ARGUMENTS = "[0][1]";
    private static final String FIRST_MESSAGE = ARGUMENTS + ".list[0]";
    private static final String ATTACHMENTS = FIRST_MESSAGE + ".attachments";
    private static final String FIRST_ATTACHMENT = ATTACHMENTS + "[0]";
    private static final String SECOND_ATTACHMENT = ATTACHMENTS + "[1]";


    private final MainStepdefs mainStepdefs;
    private final UserStepdefs userStepdefs;
    private final Map<String, MessageId> messageIdsByName;
    
    private HttpResponse response;
    private DocumentContext jsonPath;
    private List<MessageId> requestedMessageIds;
    
    @Inject
    private GetMessagesMethodStepdefs(MainStepdefs mainStepdefs, UserStepdefs userStepdefs) {
        this.mainStepdefs = mainStepdefs;
        this.userStepdefs = userStepdefs;
        this.messageIdsByName = new HashMap<>();
    }

    public MessageId getMessageId(String name) {
        return messageIdsByName.get(name);
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" and \"([^\"]*)\" mailboxes with subject \"([^\"]*)\", content \"([^\"]*)\"$")
    public void appendMessageInTwoMailboxes(String messageName, String mailbox1, String mailbox2, String subject, String content) throws Exception {
        MessageId id = appendMessage(mailbox1, ContentType.noContentType(), subject, content, NO_HEADERS);
        MailboxId mailboxId1 = mainStepdefs.jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, userStepdefs.lastConnectedUser, mailbox1).getMailboxId();
        MailboxId mailboxId2 = mainStepdefs.jmapServer.getProbe(MailboxProbeImpl.class).getMailbox(MailboxConstants.USER_NAMESPACE, userStepdefs.lastConnectedUser, mailbox2).getMailboxId();

        mainStepdefs.jmapServer.getProbe(JmapGuiceProbe.class).setInMailboxes(id, userStepdefs.lastConnectedUser, mailboxId1, mailboxId2);
        messageIdsByName.put(messageName, id);
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with subject \"([^\"]*)\", content \"([^\"]*)\"$")
    public void appendMessage(String messageName, String mailbox, String subject, String content) throws Exception {
        MessageId id = appendMessage(mailbox, ContentType.noContentType(), subject, content, NO_HEADERS);
        messageIdsByName.put(messageName, id);
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with content-type \"([^\"]*)\" subject \"([^\"]*)\", content \"([^\"]*)\"$")
    public void appendMessage(String messageName, String mailbox, String contentType, String subject, String content) throws Exception {
        MessageId id = appendMessage(mailbox, ContentType.from(contentType), subject, content, NO_HEADERS);
        messageIdsByName.put(messageName, id);
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with content-type \"([^\"]*)\" subject \"([^\"]*)\", content \"([^\"]*)\", headers$")
    public void appendMessage(String messageName, String mailbox, String contentType, String subject, String content, DataTable headers) throws Exception {
        MessageId id = appendMessage(mailbox, ContentType.from(contentType), subject, content, Optional.of(headers.asMap(String.class, String.class)));
        messageIdsByName.put(messageName, id);
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with subject \"([^\"]*)\", content \"([^\"]*)\", headers$")
    public void appendMessage(String messageName, String mailbox, String subject, String content, DataTable headers) throws Exception {
        MessageId id = appendMessage(mailbox, ContentType.noContentType(), subject, content, Optional.of(headers.asMap(String.class, String.class)));
        messageIdsByName.put(messageName, id);
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox, composed of a multipart with inlined text part and inlined html part$")
    public void appendMessageFromFileInlinedMultipart(String messageName, String mailbox) throws Exception {
        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        MessageId id = mainStepdefs.jmapServer.getProbe(MailboxProbeImpl.class).appendMessage(userStepdefs.lastConnectedUser,
                    new MailboxPath(MailboxConstants.USER_NAMESPACE, userStepdefs.lastConnectedUser, mailbox),
                    ClassLoader.getSystemResourceAsStream("eml/inlinedMultipart.eml"),
                    Date.from(dateTime.toInstant()), false, new Flags())
                .getMessageId();
        messageIdsByName.put(messageName, id);
    }

    private MessageId appendMessage(String mailbox, ContentType contentType, String subject, String content, Optional<Map<String, String>> headers) throws Exception {
        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        return mainStepdefs.jmapServer.getProbe(MailboxProbeImpl.class).appendMessage(userStepdefs.lastConnectedUser, 
                new MailboxPath(MailboxConstants.USER_NAMESPACE, userStepdefs.lastConnectedUser, mailbox),
                new ByteArrayInputStream(message(contentType, subject, content, headers).getBytes(Charsets.UTF_8)), 
                Date.from(dateTime.toInstant()), false, new Flags()).getMessageId();
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
                .map(this::entryToPair)
                .map(this::joinKeyValue)
                .collect(Collectors.joining("\r\n", "", "\r\n"));
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with two attachments$")
    public void appendHtmlMessageWithTwoAttachments(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, "eml/twoAttachments.eml");
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with two attachments in text$")
    public void appendTextMessageWithTwoAttachments(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, "eml/twoAttachmentsTextPlain.eml");
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with two same attachments in text$")
    public void appendTextMessageWithTwoSameAttachments(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, "eml/twoSameAttachments.eml");
    }

    @Given("^the user has a multipart message \"([^\"]*)\" in \"([^\"]*)\" mailbox$")
    public void appendMultipartMessageWithOneAttachments(String messageName, String arg1) throws Exception {
        appendMessage(messageName, "eml/htmlAndTextMultipartWithOneAttachment.eml");
    }

    @Given("^the user has a multipart/related message \"([^\"]*)\" in \"([^\"]*)\" mailbox$")
    public void appendMultipartRelated(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, "eml/multipartRelated.eml");
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox beginning by a long line$")
    public void appendMessageBeginningByALongLine(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, "eml/longLine.eml");
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with plain/text inline attachment$")
    public void appendMessageWithPlainTextInlineAttachment(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, "eml/embeddedMultipartWithInlineTextAttachment.eml");
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with text in main multipart and html in inner multipart$")
    public void appendMessageWithTextInMainMultipartAndHtmlInInnerMultipart(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, "eml/textInMainMultipartHtmlInInnerMultipart.eml");
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with html body and no text body$")
    public void appendMessageWithNoTextButHtml(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, "eml/noTextBodyButHtmlBody.eml");
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with inline attachment but no CID$")
    public void appendMessageWithInlineAttachmentButNoCid(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, "eml/mailWithInlinedAttachmentButNoCid.eml");
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with inline attachment and blank CID$")
    public void appendMessageWithInlineAttachmentAndBlankCid(String messageName, String mailbox) throws Throwable {
        appendMessage(messageName, "eml/mailWithInlinedAttachmentAndBlankCid.eml");
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with HTML body with many empty tags$")
    public void appendMessageWithNoPreview(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, "eml/htmlBodyWithManyEmptyTags.eml");
    }

    @Given("^the user has a message \"([^\"]*)\" in the \"([^\"]*)\" mailbox with multiple same inlined attachments \"([^\"]*)\"$")
    public void appendMessageWithSameInlinedAttachmentsToMailbox(String messageName, String mailbox, String attachmentId) throws Exception {
        appendMessage(messageName, "eml/sameInlinedImages.eml");
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with specific charset$")
    public void appendMessageWithSpecificCharset(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, "eml/windows1252charset.eml");
    }

    @Given("^the user has a message \"([^\"]*)\" in \"([^\"]*)\" mailbox with long and complicated HTML content$")
    public void appendMessageWithSpecialCase(String messageName, String mailbox) throws Exception {
        appendMessage(messageName, "eml/htmlWithLongAndComplicatedContent.eml");
    }

    private void appendMessage(String messageName, String emlFileName) throws Exception {
        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        MessageId id = mainStepdefs.jmapServer.getProbe(MailboxProbeImpl.class).appendMessage(userStepdefs.lastConnectedUser,
            new MailboxPath(MailboxConstants.USER_NAMESPACE, userStepdefs.lastConnectedUser, DefaultMailboxes.INBOX),
                ClassLoader.getSystemResourceAsStream(emlFileName),
                Date.from(dateTime.toInstant()), false, new Flags())
                    .getMessageId();
        messageIdsByName.put(messageName, id);
    }

    @When("^the user ask for messages using its accountId$")
    public void postWithAccountId() throws Exception {
        post("[[\"getMessages\", {\"accountId\": \"1\"}, \"#0\"]]");
    }

    @When("^the user ask for messages using unknown arguments$")
    public void postWithUnknownArguments() throws Exception {
        post("[[\"getMessages\", {\"WAT\": true}, \"#0\"]]");
    }

    @When("^the user ask for messages using invalid argument$")
    public void postWithInvalidArguments() throws Exception {
        post("[[\"getMessages\", {\"ids\": null}, \"#0\"]]");
    }

    @When("^the user ask for messages$")
    public void post() throws Exception {
        post("[[\"getMessages\", {\"ids\": []}, \"#0\"]]");
    }

    @When("^the user ask for messages \"(.*?)\"$")
    public void postWithAListOfIds(List<String> ids) throws Exception {
        requestedMessageIds = ids.stream()
                .map(messageIdsByName::get)
                .collect(Guavate.toImmutableList());
        askMessages(requestedMessageIds);
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
                .collect(Collectors.joining(",", "[", "]" ));
        post("[[\"getMessages\", {\"ids\": " + serializedIds + "}, \"#0\"]]");
    }

    private Function<? super String, ? extends String> toJsonString() {
        return string -> "\"" + string + "\"";
    }

    @When("^the user is getting messages \"(.*?)\" with properties \"(.*?)\"$")
    public void postWithParameters(List<String> ids, List<String> properties) throws Exception {
        requestedMessageIds = ids.stream()
                .map(messageIdsByName::get)
                .collect(Guavate.toImmutableList());

        String serializedIds = requestedMessageIds.stream()
                .map(MessageId::serialize)
                .map(toJsonString())
                .collect(Collectors.joining(",", "[", "]" ));

        String serializedProperties = properties.stream()
                .map(toJsonString())
                .collect(Collectors.joining(",", "[", "]" ));

        post("[[\"getMessages\", {\"ids\": " + serializedIds + ", \"properties\": " + serializedProperties + "}, \"#0\"]]");
    }

    private Pair<String, String> entryToPair(Map.Entry<String, String> entry) {
        return Pair.with(entry.getKey(), entry.getValue());
    }

    private String joinKeyValue(Pair<String, String> pair) {
        return Joiner.on(": ").join(pair);
    }

    private void post(String requestBody) throws Exception {
        response = Request.Post(mainStepdefs.baseUri().setPath("/jmap").build())
            .addHeader("Authorization", userStepdefs.tokenByUser.get(userStepdefs.lastConnectedUser).serialize())
            .addHeader("Accept", org.apache.http.entity.ContentType.APPLICATION_JSON.getMimeType())
            .bodyString(requestBody, org.apache.http.entity.ContentType.APPLICATION_JSON)
            .execute()
            .returnResponse();
        jsonPath = JsonPath.using(Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS)).parse(response.getEntity().getContent());
    }

    @Then("^an error \"([^\"]*)\" is returned$")
    public void error(String type) throws Exception {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
        assertThat(jsonPath.<String>read(NAME)).isEqualTo("error");
        assertThat(jsonPath.<String>read(ARGUMENTS + ".type")).isEqualTo(type);
    }

    @Then("^no error is returned$")
    public void noError() throws Exception {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
        assertThat(jsonPath.<String>read(NAME)).isEqualTo("messages");
    }

    @Then("^the list of unknown messages is empty$")
    public void assertNotFoundIsEmpty() {
        assertThat(jsonPath.<List<String>>read(ARGUMENTS + ".notFound")).isEmpty();
    }

    @Then("^the list of messages is empty$")
    public void assertListIsEmpty() {
        assertThat(jsonPath.<List<String>>read(ARGUMENTS + ".list")).isEmpty();
    }

    @Then("^the description is \"(.*?)\"$")
    public void assertDescription(String description) throws Exception {
        assertThat(jsonPath.<String>read(ARGUMENTS + ".description")).isEqualTo(description);
    }

    @Then("^the notFound list should contain \"([^\"]*)\"$")
    public void assertNotFoundListContains(String ids) throws Exception {
        assertThat(jsonPath.<List<String>>read(ARGUMENTS + ".notFound")).contains(ids);
    }

    @Then("^the notFound list should contain the requested message id$")
    public void assertNotFoundListContainsRequestedMessages() throws Exception {
        ImmutableList<String> elements = requestedMessageIds.stream().map(MessageId::serialize).collect(Guavate.toImmutableList());
        assertThat(jsonPath.<List<String>>read(ARGUMENTS + ".notFound")).containsExactlyElementsOf(elements);
    }

    
    @Then("^the list should contain (\\d+) message$")
    public void assertListContains(int numberOfMessages) throws Exception {
        assertThat(jsonPath.<List<String>>read(ARGUMENTS + ".list")).hasSize(numberOfMessages);
    }
    
    @Then("^the id of the message is \"([^\"]*)\"$")
    public void assertIdOfTheFirstMessage(String messageName) throws Exception {
        MessageId id = messageIdsByName.get(messageName);
        assertThat(jsonPath.<String>read(FIRST_MESSAGE + ".id")).isEqualTo(id.serialize());
    }

    @Then("^the message is in \"([^\"]*)\" mailboxes")
    public void assertMailboxIdsOfTheFirstMessage(String mailboxIds) throws Exception {
        List<String> values = Splitter.on(",")
            .splitToList(mailboxIds).stream()
            .map(Throwing.function(name -> mainStepdefs.jmapServer
                .getProbe(MailboxProbeImpl.class)
                .getMailbox(MailboxConstants.USER_NAMESPACE, userStepdefs.lastConnectedUser, name)
                .getMailboxId()
                .serialize()))
            .distinct()
            .collect(Guavate.toImmutableList());
        assertThat(jsonPath.<JSONArray>read(FIRST_MESSAGE + ".mailboxIds"))
            .hasSize(2)
            .containsOnlyElementsOf(values);
    }

    @Then("^the threadId of the message is \"([^\"]*)\"$")
    public void assertThreadIdOfTheFirstMessage(String threadId) throws Exception {
        MessageId id = messageIdsByName.get(threadId);
        assertThat(jsonPath.<String>read(FIRST_MESSAGE + ".threadId")).isEqualTo(id.serialize());
    }

    @Then("^the subject of the message is \"([^\"]*)\"$")
    public void assertSubjectOfTheFirstMessage(String subject) throws Exception {
        assertThat(jsonPath.<String>read(FIRST_MESSAGE + ".subject")).isEqualTo(subject);
    }

    @Then("^the textBody of the message is \"([^\"]*)\"$")
    public void assertTextBodyOfTheFirstMessage(String textBody) throws Exception {
        assertThat(jsonPath.<String>read(FIRST_MESSAGE + ".textBody")).isEqualTo(StringEscapeUtils.unescapeJava(textBody));
    }

    @Then("^the htmlBody of the message is \"([^\"]*)\"$")
    public void assertHtmlBodyOfTheFirstMessage(String htmlBody) throws Exception {
        assertThat(jsonPath.<String>read(FIRST_MESSAGE + ".htmlBody")).isEqualTo(StringEscapeUtils.unescapeJava(htmlBody));
    }

    @Then("^the isUnread of the message is \"([^\"]*)\"$")
    public void assertIsUnreadOfTheFirstMessage(String isUnread) throws Exception {
        assertThat(jsonPath.<Boolean>read(FIRST_MESSAGE + ".isUnread")).isEqualTo(Boolean.valueOf(isUnread));
    }

    @Then("^the preview of the message is \"([^\"]*)\"$")
    public void assertPreviewOfTheFirstMessage(String preview) throws Exception {
        String actual = jsonPath.<String>read(FIRST_MESSAGE + ".preview").replace("\n", " ");
        assertThat(actual).isEqualToIgnoringWhitespace(StringEscapeUtils.unescapeJava(preview));
    }

    @Then("^the preview of the message is not empty$")
    public void assertPreviewOfTheFirstMessageIsNotEmpty() throws Exception {
        String actual = jsonPath.<String>read(FIRST_MESSAGE + ".preview");
        assertThat(actual).isNotEmpty();
    }

    @Then("^the preview should not contain consecutive spaces or blank characters$")
    public void assertPreviewShouldBeNormalized() throws Exception {
        String actual = jsonPath.<String>read(FIRST_MESSAGE + ".preview");
        assertThat(actual).hasSize(MessagePreviewGenerator.MAX_PREVIEW_LENGTH)
                .doesNotMatch("  ")
                .doesNotContain(StringUtils.CR)
                .doesNotContain(StringUtils.LF);
    }

    @Then("^the headers of the message contains:$")
    public void assertHeadersOfTheFirstMessage(DataTable headers) throws Exception {
        assertThat(jsonPath.<Map<String, String>>read(FIRST_MESSAGE + ".headers")).containsAllEntriesOf(headers.asMap(String.class, String.class));
    }

    @Then("^the date of the message is \"([^\"]*)\"$")
    public void assertDateOfTheFirstMessage(String date) throws Exception {
        assertThat(jsonPath.<String>read(FIRST_MESSAGE + ".date")).isEqualTo(date);
    }

    @Then("^the hasAttachment of the message is \"([^\"]*)\"$")
    public void assertHasAttachmentOfTheFirstMessage(String hasAttachment) throws Exception {
        assertThat(jsonPath.<Boolean>read(FIRST_MESSAGE + ".hasAttachment")).isEqualTo(Boolean.valueOf(hasAttachment));
    }

    @Then("^the list of attachments of the message is empty$")
    public void assertAttachmentsOfTheFirstMessageIsEmpty() throws Exception {
        assertThat(jsonPath.<List<Object>>read(ATTACHMENTS)).isEmpty();
    }

    @Then("^the property \"([^\"]*)\" of the message is null$")
    public void assertPropertyIsNull(String property) throws Exception {
        assertThat(jsonPath.<String>read(FIRST_MESSAGE + "." + property + ".date")).isNull();
    }

    @Then("^the list of attachments of the message contains (\\d+) attachments?$")
    public void assertAttachmentsHasSize(int numberOfAttachments) throws Exception {
        assertThat(jsonPath.<List<Object>>read(ATTACHMENTS)).hasSize(numberOfAttachments);
    }

    @Then("^the list of attachments of the message contains only one attachment with cid \"([^\"]*)\"?$")
    public void assertAttachmentsAndItsCid(String cid) throws Exception {
        assertThat(jsonPath.<String>read(FIRST_ATTACHMENT + ".cid")).isEqualTo(cid);
    }

    @Then("^the first attachment is:$")
    public void assertFirstAttachment(DataTable attachmentProperties) throws Exception {
        assertAttachment(FIRST_ATTACHMENT, attachmentProperties);
    }

    @Then("^the second attachment is:$")
    public void assertSecondAttachment(DataTable attachmentProperties) throws Exception {
        assertAttachment(SECOND_ATTACHMENT, attachmentProperties);
    }

    @Then("^the preview of the message contains: (.*)$")
    public void assertPreviewOfMessageShouldBePrintedWithEncoding(List<String> preview) throws Exception {
        String actual = jsonPath.<String>read(FIRST_MESSAGE + ".preview");
        assertThat(actual).contains(preview);
    }

    private void assertAttachment(String attachment, DataTable attachmentProperties) {
        attachmentProperties.asList(TableRow.class)
            .forEach(entry -> assertThat(jsonPath.<Object>read(attachment + "." + entry.getKey())).isEqualTo(entry.getValue()));
    }
}
