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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.mail.Flags;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.james.jmap.methods.integration.cucumber.util.TableRow;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.javatuples.Pair;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

import cucumber.api.DataTable;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.runtime.java.guice.ScenarioScoped;

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

    private HttpResponse response;
    private DocumentContext jsonPath;

    @Inject
    private GetMessagesMethodStepdefs(MainStepdefs mainStepdefs, UserStepdefs userStepdefs) {
        this.mainStepdefs = mainStepdefs;
        this.userStepdefs = userStepdefs;
    }

    @Given("^the user has a message in \"([^\"]*)\" mailbox with subject \"([^\"]*)\" and content \"([^\"]*)\"$")
    public void appendMessage(String mailbox, String subject, String content) throws Throwable {
        appendMessage(mailbox, ContentType.noContentType(), subject, content, NO_HEADERS);
    }

    @Given("^the user has a message in \"([^\"]*)\" mailbox with content-type \"([^\"]*)\" subject \"([^\"]*)\" and content \"([^\"]*)\"$")
    public void appendMessage(String mailbox, String contentType, String subject, String content) throws Throwable {
        appendMessage(mailbox, ContentType.from(contentType), subject, content, NO_HEADERS);
    }

    @Given("^the user has a message in \"([^\"]*)\" mailbox with subject \"([^\"]*)\" and content \"([^\"]*)\" with headers$")
    public void appendMessage(String mailbox, String subject, String content, DataTable headers) throws Throwable {
        appendMessage(mailbox, ContentType.noContentType(), subject, content, Optional.of(headers.asMap(String.class, String.class)));
    }

    @Given("^the user has a message in \"([^\"]*)\" mailbox, composed of a multipart with inlined text part and inlined html part$")
    public void appendMessageFromFileInlinedMultipart(String mailbox) throws Throwable {
        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        mainStepdefs.jmapServer.serverProbe().appendMessage(userStepdefs.lastConnectedUser,
            new MailboxPath(MailboxConstants.USER_NAMESPACE, userStepdefs.lastConnectedUser, mailbox),
            ClassLoader.getSystemResourceAsStream("eml/inlinedMultipart.eml"),
            Date.from(dateTime.toInstant()), false, new Flags());
    }

    private void appendMessage(String mailbox, ContentType contentType, String subject, String content, Optional<Map<String, String>> headers) throws Exception {
        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        mainStepdefs.jmapServer.serverProbe().appendMessage(userStepdefs.lastConnectedUser, 
                new MailboxPath(MailboxConstants.USER_NAMESPACE, userStepdefs.lastConnectedUser, mailbox),
                new ByteArrayInputStream(message(contentType, subject, content, headers).getBytes(Charsets.UTF_8)), 
                Date.from(dateTime.toInstant()), false, new Flags());
    }

    private String message(ContentType contentType, String subject, String content, Optional<Map<String,String>> headers) {
        return serialize(headers) + contentType.serializeToHeader() + "Subject: " + subject + "\r\n\r\n" + content;
    }

    private String serialize(Optional<Map<String,String>> headers) {
        return headers
                .map(map -> map.entrySet())
                .map(entriesToString())
                .orElse("");
    }

    private Function<Set<Entry<String, String>>, String> entriesToString() {
        return entries -> entries.stream()
                .map(this::entryToPair)
                .map(this::joinKeyValue)
                .collect(Collectors.joining("\r\n", "", "\r\n"));
    }

    @Given("^the user has a message in \"([^\"]*)\" mailbox with two attachments$")
    public void appendHtmlMessageWithTwoAttachments(String mailbox) throws Throwable {
        appendMessage("eml/twoAttachments.eml");
    }

    @Given("^the user has a message in \"([^\"]*)\" mailbox with two attachments in text$")
    public void appendTextMessageWithTwoAttachments(String arg1) throws Throwable {
        appendMessage("eml/twoAttachmentsTextPlain.eml");
    }

    @Given("^the user has a multipart message in \"([^\"]*)\" mailbox$")
    public void appendMultipartMessageWithOneAttachments(String arg1) throws Throwable {
        appendMessage("eml/htmlAndTextMultipartWithOneAttachment.eml");
    }

    @Given("^the user has a multipart/related message in \"([^\"]*)\" mailbox$")
    public void appendMultipartRelated(String arg1) throws Throwable {
        appendMessage("eml/multipartRelated.eml");
    }

    private void appendMessage(String emlFileName) throws Exception {
        ZonedDateTime dateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z");
        mainStepdefs.jmapServer.serverProbe().appendMessage(userStepdefs.lastConnectedUser, 
                new MailboxPath(MailboxConstants.USER_NAMESPACE, userStepdefs.lastConnectedUser, "inbox"),
                ClassLoader.getSystemResourceAsStream(emlFileName), 
                Date.from(dateTime.toInstant()), false, new Flags());
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
    public void post() throws Throwable {
        post("[[\"getMessages\", {\"ids\": []}, \"#0\"]]");
    }

    @When("^the user ask for messages \"(.*?)\"$")
    public void postWithAListOfIds(String ids) throws Throwable {
        post("[[\"getMessages\", {\"ids\": " + ids + "}, \"#0\"]]");
    }

    @When("^the user is getting his messages with parameters$")
    public void postWithParameters(DataTable parameters) throws Throwable {
        String payload = 
                parameters.asMap(String.class, String.class)
                    .entrySet()
                    .stream()
                    .map(this::entryToPair)
                    .map(this::quoteIndex)
                    .map(this::joinKeyValue)
                    .collect(Collectors.joining(",", "{", "}"));
        
        post("[[\"getMessages\", " + payload + ", \"#0\"]]");
    }

    private Pair<String, String> entryToPair(Map.Entry<String, String> entry) {
        return Pair.with(entry.getKey(), entry.getValue());
    }

    private Pair<String, String> quoteIndex(Pair<String, String> pair) {
        return Pair.with(String.format("\"%s\"", pair.getValue0()), pair.getValue1());
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
    public void error(String type) throws Throwable {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
        assertThat(jsonPath.<String>read(NAME)).isEqualTo("error");
        assertThat(jsonPath.<String>read(ARGUMENTS + ".type")).isEqualTo(type);
    }

    @Then("^no error is returned$")
    public void noError() throws Throwable {
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
    public void assertDescription(String description) throws Throwable {
        assertThat(jsonPath.<String>read(ARGUMENTS + ".description")).isEqualTo(description);
    }

    @Then("^the notFound list should contains \"([^\"]*)\"$")
    public void assertNotFoundListContains(String ids) throws Throwable {
        assertThat(jsonPath.<List<String>>read(ARGUMENTS + ".notFound")).contains(ids);
    }

    @Then("^the list should contain (\\d+) message$")
    public void assertListContains(int numberOfMessages) throws Throwable {
        assertThat(jsonPath.<List<String>>read(ARGUMENTS + ".list")).hasSize(numberOfMessages);
    }
    
    @Then("^the id of the message is \"([^\"]*)\"$")
    public void assertIdOfTheFirstMessage(String id) throws Throwable {
        assertThat(jsonPath.<String>read(FIRST_MESSAGE + ".id")).isEqualTo(id);
    }

    @Then("^the threadId of the message is \"([^\"]*)\"$")
    public void assertThreadIdOfTheFirstMessage(String threadId) throws Throwable {
        assertThat(jsonPath.<String>read(FIRST_MESSAGE + ".threadId")).isEqualTo(threadId);
    }

    @Then("^the subject of the message is \"([^\"]*)\"$")
    public void assertSubjectOfTheFirstMessage(String subject) throws Throwable {
        assertThat(jsonPath.<String>read(FIRST_MESSAGE + ".subject")).isEqualTo(subject);
    }

    @Then("^the textBody of the message is \"([^\"]*)\"$")
    public void assertTextBodyOfTheFirstMessage(String textBody) throws Throwable {
        assertThat(jsonPath.<String>read(FIRST_MESSAGE + ".textBody")).isEqualTo(StringEscapeUtils.unescapeJava(textBody));
    }

    @Then("^the htmlBody of the message is \"([^\"]*)\"$")
    public void assertHtmlBodyOfTheFirstMessage(String htmlBody) throws Throwable {
        assertThat(jsonPath.<String>read(FIRST_MESSAGE + ".htmlBody")).isEqualTo(StringEscapeUtils.unescapeJava(htmlBody));
    }

    @Then("^the isUnread of the message is \"([^\"]*)\"$")
    public void assertIsUnreadOfTheFirstMessage(String isUnread) throws Throwable {
        assertThat(jsonPath.<Boolean>read(FIRST_MESSAGE + ".isUnread")).isEqualTo(Boolean.valueOf(isUnread));
    }

    @Then("^the preview of the message is \"([^\"]*)\"$")
    public void assertPreviewOfTheFirstMessage(String preview) throws Throwable {
        String actual = jsonPath.<String>read(FIRST_MESSAGE + ".preview").replace("\n", " ");
        assertThat(actual).isEqualToIgnoringWhitespace(StringEscapeUtils.unescapeJava(preview));
    }

    @Then("^the headers of the message contains:$")
    public void assertHeadersOfTheFirstMessage(DataTable headers) throws Throwable {
        assertThat(jsonPath.<Map<String, String>>read(FIRST_MESSAGE + ".headers")).containsAllEntriesOf(headers.asMap(String.class, String.class));
    }

    @Then("^the date of the message is \"([^\"]*)\"$")
    public void assertDateOfTheFirstMessage(String date) throws Throwable {
        assertThat(jsonPath.<String>read(FIRST_MESSAGE + ".date")).isEqualTo(date);
    }

    @Then("^the hasAttachment of the message is \"([^\"]*)\"$")
    public void assertHasAttachmentOfTheFirstMessage(String hasAttachment) throws Throwable {
        assertThat(jsonPath.<Boolean>read(FIRST_MESSAGE + ".hasAttachment")).isEqualTo(Boolean.valueOf(hasAttachment));
    }

    @Then("^the list of attachments of the message is empty$")
    public void assertAttachmentsOfTheFirstMessageIsEmpty() throws Throwable {
        assertThat(jsonPath.<List<Object>>read(ATTACHMENTS)).isEmpty();
    }

    @Then("^the property \"([^\"]*)\" of the message is null$")
    public void assertPropertyIsNull(String property) throws Throwable {
        assertThat(jsonPath.<String>read(FIRST_MESSAGE + "." + property + ".date")).isNull();
    }

    @Then("^the list of attachments of the message contains (\\d+) attachments?$")
    public void assertAttachmentsHasSize(int numberOfAttachments) throws Throwable {
        assertThat(jsonPath.<List<Object>>read(ATTACHMENTS)).hasSize(numberOfAttachments);
    }

    @Then("^the first attachment is:$")
    public void assertFirstAttachment(DataTable attachmentProperties) throws Throwable {
        assertAttachment(FIRST_ATTACHMENT, attachmentProperties);
    }

    @Then("^the second attachment is:$")
    public void assertSecondAttachment(DataTable attachmentProperties) throws Throwable {
        assertAttachment(SECOND_ATTACHMENT, attachmentProperties);
    }

    private void assertAttachment(String attachment, DataTable attachmentProperties) {
        attachmentProperties.asList(TableRow.class)
            .stream()
            .forEach(entry -> assertThat(jsonPath.<Object>read(attachment + "." + entry.getKey())).isEqualTo(entry.getValue()));
    }
}
