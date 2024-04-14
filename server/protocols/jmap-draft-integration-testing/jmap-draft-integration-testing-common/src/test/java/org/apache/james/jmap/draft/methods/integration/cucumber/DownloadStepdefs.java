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

import static org.apache.james.jmap.JmapURIBuilder.baseUri;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.client.utils.URIBuilder;
import org.apache.james.core.Username;
import org.apache.james.jmap.AccessToken;
import org.apache.james.jmap.draft.model.AttachmentAccessToken;
import org.apache.james.mailbox.MessageManager.AppendCommand;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.ByteSourceContent;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mime4j.codec.DecoderUtil;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.util.io.InputStreamUtils;

import com.google.common.base.CharMatcher;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import io.cucumber.guice.ScenarioScoped;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

@ScenarioScoped
public class DownloadStepdefs {

    private static final String ONE_ATTACHMENT_EML_ATTACHMENT_BLOB_ID = "913ee2d903a68c3e8cb51169d34cf9ec257323c1bee254c0c7ab820930fdb8e7";
    private static final String EXPIRED_ATTACHMENT_TOKEN = "usera@domain.tld_"
            + "2016-06-29T13:41:22.124Z_"
            + "DiZa0O14MjLWrAA8P6MG35Gt5CBp7mt5U1EH/M++rIoZK7nlGJ4dPW0dvZD7h4m3o5b/Yd8DXU5x2x4+s0HOOKzD7X0RMlsU7JHJMNLvTvRGWF/C+MUyC8Zce7DtnRVPEQX2uAZhL2PBABV07Vpa8kH+NxoS9CL955Bc1Obr4G+KN2JorADlocFQA6ElXryF5YS/HPZSvq1MTC6aJIP0ku8WRpRnbwgwJnn26YpcHXcJjbkCBtd9/BhlMV6xNd2hTBkfZmYdoNo+UKBaXWzLxAlbLuxjpxwvDNJfOEyWFPgHDoRvzP+G7KzhVWjanHAHrhF0GilEa/MKpOI1qHBSwA==";
    private static final String INVALID_ATTACHMENT_TOKEN = "usera@domain.tld_"
            + "2015-06-29T13:41:22.124Z_"
            + "DiZa0O14MjLWrAA8P6MG35Gt5CBp7mt5U1EH/M++rIoZK7nlGJ4dPW0dvZD7h4m3o5b/Yd8DXU5x2x4+s0HOOKzD7X0RMlsU7JHJMNLvTvRGWF/C+MUyC8Zce7DtnRVPEQX2uAZhL2PBABV07Vpa8kH+NxoS9CL955Bc1Obr4G+KN2JorADlocFQA6ElXryF5YS/HPZSvq1MTC6aJIP0ku8WRpRnbwgwJnn26YpcHXcJjbkCBtd9/BhlMV6xNd2hTBkfZmYdoNo+UKBaXWzLxAlbLuxjpxwvDNJfOEyWFPgHDoRvzP+G7KzhVWjanHAHrhF0GilEa/MKpOI1qHBSwA==";
    private static final String UTF8_CONTENT_DIPOSITION_START = "Content-Disposition: attachment; filename*=\"";

    private final UserStepdefs userStepdefs;
    private final MainStepdefs mainStepdefs;
    private final GetMessagesMethodStepdefs getMessagesMethodStepdefs;
    private HttpResponse response;
    private Multimap<String, String> attachmentsByMessageId;
    private Map<String, String> blobIdByAttachmentId;
    private Map<String, MessageId> inputToMessageId;
    private Map<AttachmentAccessTokenKey, AttachmentAccessToken> attachmentAccessTokens;

    @Inject
    private DownloadStepdefs(MainStepdefs mainStepdefs, UserStepdefs userStepdefs, GetMessagesMethodStepdefs getMessagesMethodStepdefs) {
        this.mainStepdefs = mainStepdefs;
        this.userStepdefs = userStepdefs;
        this.getMessagesMethodStepdefs = getMessagesMethodStepdefs;
        this.attachmentsByMessageId = ArrayListMultimap.create();
        this.blobIdByAttachmentId = new HashMap<>();
        this.attachmentAccessTokens = new HashMap<>();
        this.inputToMessageId = new HashMap<>();
    }

    @Given("^\"([^\"]*)\" mailbox \"([^\"]*)\" contains a message \"([^\"]*)\"$")
    public void appendMessageToMailbox(String user, String mailbox, String messageId) throws Throwable {
        MailboxPath mailboxPath = MailboxPath.forUser(Username.of(user), mailbox);

        ComposedMessageId composedMessageId = mainStepdefs.mailboxProbe.appendMessage(user, mailboxPath,
            AppendCommand.from(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/oneAttachment.eml")));

        inputToMessageId.put(messageId, composedMessageId.getMessageId());
    }

    @Given("^\"([^\"]*)\" mailbox \"([^\"]*)\" contains a big message \"([^\"]*)\"$")
    public void appendBigMessageToMailbox(String user, String mailbox, String messageId) throws Throwable {
        MailboxPath mailboxPath = MailboxPath.forUser(Username.of(user), mailbox);

        ComposedMessageId composedMessageId = mainStepdefs.mailboxProbe.appendMessage(user, mailboxPath,
            AppendCommand.from(new ByteContent(
                Strings.repeat("header: 0123456789\r\n", 128 * 1024)
                    .getBytes(StandardCharsets.UTF_8))));

        inputToMessageId.put(messageId, composedMessageId.getMessageId());
    }

    @Given("^\"([^\"]*)\" mailbox \"([^\"]*)\" contains a message \"([^\"]*)\" with an attachment \"([^\"]*)\"$")
    public void appendMessageWithAttachmentToMailbox(String user, String mailbox, String messageId, String attachmentId) throws Throwable {
        MailboxPath mailboxPath = MailboxPath.forUser(Username.of(user), mailbox);

        ComposedMessageId composedMessageId = mainStepdefs.mailboxProbe.appendMessage(user, mailboxPath,
            AppendCommand.from(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/oneAttachment.eml")));

        retrieveAndSaveAttachmentDetails(user, messageId, attachmentId, composedMessageId);
    }

    @Given("^\"([^\"]*)\" mailbox \"([^\"]*)\" contains a message \"([^\"]*)\" with an attachment \"([^\"]*)\" having \"([^\"]*)\" contentType$")
    public void appendMessageWithAttachmentToMailbox(String user, String mailbox, String messageId, String attachmentId, String contentType) throws Throwable {
        MailboxPath mailboxPath = MailboxPath.forUser(Username.of(user), mailbox);

        InputStream message = InputStreamUtils.concat(
            ClassLoaderUtils.getSystemResourceAsSharedStream("eml/oneAttachment-part1.eml"),
            new ByteArrayInputStream(contentType.getBytes(StandardCharsets.UTF_8)),
            ClassLoaderUtils.getSystemResourceAsSharedStream("eml/oneAttachment-part2.eml"));

        ComposedMessageId composedMessageId = mainStepdefs.mailboxProbe.appendMessage(user, mailboxPath,
            AppendCommand.from(ByteSourceContent.of(message)));

        retrieveAndSaveAttachmentDetails(user, messageId, attachmentId, composedMessageId);
    }

    @Given("^\"([^\"]*)\" mailbox \"([^\"]*)\" contains a message \"([^\"]*)\" with an inlined attachment \"([^\"]*)\"$")
    public void appendMessageWithInlinedAttachmentToMailbox(String user, String mailbox, String messageId, String attachmentId) throws Throwable {
        MailboxPath mailboxPath = MailboxPath.forUser(Username.of(user), mailbox);

        ComposedMessageId composedMessageId = mainStepdefs.mailboxProbe.appendMessage(user, mailboxPath,
            AppendCommand.from(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/oneInlinedImage.eml")));

        retrieveAndSaveAttachmentDetails(user, messageId, attachmentId, composedMessageId);
    }

    public void retrieveAndSaveAttachmentDetails(String user, String messageId, String attachmentId, ComposedMessageId composedMessageId) throws MailboxException {
        AttachmentId mailboxAttachmentId = mainStepdefs.messageIdProbe
            .retrieveAttachmentIds(composedMessageId.getMessageId(), Username.of(user))
            .get(0);

        inputToMessageId.put(messageId, composedMessageId.getMessageId());
        attachmentsByMessageId.put(messageId, attachmentId);
        blobIdByAttachmentId.put(attachmentId, mailboxAttachmentId.getId());
    }

    @Given("^\"([^\"]*)\" mailbox \"([^\"]*)\" contains a message \"([^\"]*)\" with multiple same inlined attachments \"([^\"]*)\"$")
    public void appendMessageWithSameInlinedAttachmentsToMailbox(String user, String mailbox, String messageName, String attachmentId) throws Throwable {
        MailboxPath mailboxPath = MailboxPath.forUser(Username.of(user), mailbox);

        ComposedMessageId composedMessageId = mainStepdefs.mailboxProbe.appendMessage(user, mailboxPath,
            AppendCommand.from(ClassLoaderUtils.getSystemResourceAsSharedStream("eml/sameInlinedImages.eml")));

        retrieveAndSaveAttachmentDetails(user, messageName, attachmentId, composedMessageId);
    }

    @When("^\"([^\"]*)\" checks for the availability of the attachment endpoint$")
    public void optionDownload(String username) throws Throwable {
        AccessToken accessToken = userStepdefs.authenticate(username);
        URI target = baseUri(mainStepdefs.jmapServer).setPath("/download/" + ONE_ATTACHMENT_EML_ATTACHMENT_BLOB_ID).build();
        Request request = Request.Options(target);
        if (accessToken != null) {
            request.addHeader("Authorization", accessToken.asString());
        }
        response = request.execute().returnResponse();
    }

    @When("^\"([^\"]*)\" downloads \"([^\"]*)\"$")
    public void downloads(String username, String blobId) throws Throwable {
        String attachmentIdOrMessageId = Optional.ofNullable(blobIdByAttachmentId.get(blobId))
            .orElse(Optional.ofNullable(inputToMessageId.get(blobId))
                .map(MessageId::serialize)
                .orElse(null));

        downLoad(username, attachmentIdOrMessageId);
    }

    @When("^\"([^\"]*)\" downloads \"([^\"]*)\" using query parameter strategy$")
    public void downloadsUsingQueryParameter(String username, String blobId) throws Throwable {
        String attachmentIdOrMessageId = Optional.ofNullable(blobIdByAttachmentId.get(blobId))
            .orElse(Optional.ofNullable(inputToMessageId.get(blobId))
                .map(MessageId::serialize)
                .orElse(null));
        URIBuilder uriBuilder = baseUri(mainStepdefs.jmapServer).setPath("/download/" + attachmentIdOrMessageId);
        response = queryParameterDownloadRequest(uriBuilder, attachmentIdOrMessageId, username).execute().returnResponse();
    }

    @When("^un-authenticated user downloads \"([^\"]*)\"$")
    public void downloadsUnAuthenticated(String blobId) throws Throwable {
        String attachmentIdOrMessageId = Optional.ofNullable(blobIdByAttachmentId.get(blobId))
            .orElse(Optional.ofNullable(inputToMessageId.get(blobId))
                .map(MessageId::serialize)
                .orElse(null));

        response = Request.Get(
            baseUri(mainStepdefs.jmapServer)
                .setPath("/download/" + attachmentIdOrMessageId)
                .build())
            .execute()
            .returnResponse();
    }

    @When("^\"([^\"]*)\" downloads the message by its blobId$")
    public void downloads(String username) throws Throwable {
        downLoad(username, getMessagesMethodStepdefs.getBlobId());
    }

    private void downLoad(String username, String blobId) throws IOException, URISyntaxException {
        URIBuilder uriBuilder = baseUri(mainStepdefs.jmapServer).setPath("/download/" + blobId);
        response = authenticatedDownloadRequest(uriBuilder, blobId, username).execute().returnResponse();
    }

    private Request authenticatedDownloadRequest(URIBuilder uriBuilder, String blobId, String username) throws URISyntaxException {
        AccessToken accessToken = userStepdefs.authenticate(username);
        AttachmentAccessTokenKey key = new AttachmentAccessTokenKey(username, blobId);
        if (attachmentAccessTokens.containsKey(key)) {
            uriBuilder.addParameter("access_token", attachmentAccessTokens.get(key).serialize());
            return Request.Get(uriBuilder.build());
        } else {
            Request request = Request.Get(uriBuilder.build());
            if (accessToken != null) {
                request.addHeader("Authorization", accessToken.asString());
            }
            return request;
        }
    }

    private Request queryParameterDownloadRequest(URIBuilder uriBuilder, String blobId, String username) throws URISyntaxException {
        AccessToken accessToken = userStepdefs.authenticate(username);
        AttachmentAccessTokenKey key = new AttachmentAccessTokenKey(username, blobId);
        uriBuilder.addParameter("access_token", attachmentAccessTokens.get(key).serialize());
        return Request.Get(uriBuilder.build());
    }

    @When("^\"([^\"]*)\" is trusted for attachment \"([^\"]*)\"$")
    public void attachmentAccessTokenFor(String username, String attachmentId) throws Throwable {
        userStepdefs.connectUser(username);
        trustForBlobId(blobIdByAttachmentId.get(attachmentId), username);
    }

    private static class AttachmentAccessTokenKey {

        private String username;
        private String blobId;

        public AttachmentAccessTokenKey(String username, String blobId) {
            this.username = username;
            this.blobId = blobId;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof AttachmentAccessTokenKey) {
                AttachmentAccessTokenKey other = (AttachmentAccessTokenKey) obj;
                return Objects.equal(username, other.username)
                    && Objects.equal(blobId, other.blobId);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(username, blobId);
        }

        @Override
        public String toString() {
            return MoreObjects
                    .toStringHelper(this)
                    .add("username", username)
                    .add("blobId", blobId)
                    .toString();
        }
    }

    private void trustForBlobId(String blobId, String username) throws Exception {
        Response tokenGenerationResponse = Request.Post(baseUri(mainStepdefs.jmapServer).setPath("/download/" + blobId).build())
            .addHeader("Authorization", userStepdefs.authenticate(username).asString())
            .execute();
        String serializedAttachmentAccessToken = tokenGenerationResponse.returnContent().asString();
        attachmentAccessTokens.put(
                new AttachmentAccessTokenKey(username, blobId),
                AttachmentAccessToken.from(
                    serializedAttachmentAccessToken,
                    blobId));
    }

    @When("^\"([^\"]*)\" downloads \"(?:[^\"]*)\" with a valid authentication token but a bad blobId$")
    public void downloadsWithValidToken(String username) {
        userStepdefs.execWithUser(username, () -> {
            URIBuilder uriBuilder = baseUri(mainStepdefs.jmapServer).setPath("/download/badblobId");
            response = Request.Get(uriBuilder.build())
                .addHeader("Authorization", userStepdefs.authenticate(username).asString())
                .execute()
                .returnResponse();

        });
    }

    @When("^\"(?:[^\"]*)\" downloads \"([^\"]*)\" without any authentication token$")
    public void getDownloadWithoutToken(String attachmentId) throws Exception {
        String blobId = blobIdByAttachmentId.get(attachmentId);
        response = Request.Get(baseUri(mainStepdefs.jmapServer).setPath("/download/" + blobId).build())
            .execute()
            .returnResponse();
    }

    @When("^\"(?:[^\"]*)\" downloads \"([^\"]*)\" with an empty authentication token$")
    public void getDownloadWithEmptyToken(String attachmentId) throws Exception {
        String blobId = blobIdByAttachmentId.get(attachmentId);
        response = Request.Get(
                baseUri(mainStepdefs.jmapServer)
                    .setPath("/download/" + blobId)
                    .addParameter("access_token", "")
                    .build())
                .execute()
                .returnResponse();
    }

    @When("^\"(?:[^\"]*)\" downloads \"([^\"]*)\" with a bad authentication token$")
    public void getDownloadWithBadToken(String attachmentId) throws Exception {
        String blobId = blobIdByAttachmentId.get(attachmentId);
        response = Request.Get(
                baseUri(mainStepdefs.jmapServer)
                    .setPath("/download/" + blobId)
                    .addParameter("access_token", "bad")
                    .build())
                .execute()
                .returnResponse();
    }

    @When("^\"(?:[^\"]*)\" downloads \"([^\"]*)\" with an invalid authentication token$")
    public void getDownloadWithUnknownToken(String attachmentId) throws Exception {
        String blobId = blobIdByAttachmentId.get(attachmentId);
        response = Request.Get(
                baseUri(mainStepdefs.jmapServer)
                    .setPath("/download/" + blobId)
                    .addParameter("access_token", INVALID_ATTACHMENT_TOKEN)
                    .build())
                .execute()
                .returnResponse();
    }

    @When("^\"([^\"]*)\" downloads \"([^\"]*)\" without blobId parameter$")
    public void getDownloadWithoutBlobId(String username, String attachmentId) throws Throwable {
        String blobId = blobIdByAttachmentId.get(attachmentId);

        URIBuilder uriBuilder = baseUri(mainStepdefs.jmapServer).setPath("/download/");
        trustForBlobId(blobId, username);
        AttachmentAccessTokenKey key = new AttachmentAccessTokenKey(username, blobId);
        uriBuilder.addParameter("access_token", attachmentAccessTokens.get(key).serialize());
        response = Request.Get(uriBuilder.build())
            .execute()
            .returnResponse();
    }

    @When("^\"([^\"]*)\" downloads \"([^\"]*)\" with wrong blobId$")
    public void getDownloadWithWrongBlobId(String username, String attachmentId) throws Throwable {
        String blobId = blobIdByAttachmentId.get(attachmentId);

        URIBuilder uriBuilder = baseUri(mainStepdefs.jmapServer).setPath("/download/badbadbadbadbadbadbadbadbadbadbadbadbadb");
        trustForBlobId(blobId, username);
        AttachmentAccessTokenKey key = new AttachmentAccessTokenKey(username, blobId);
        uriBuilder.addParameter("access_token", attachmentAccessTokens.get(key).serialize());
        response = Request.Get(uriBuilder.build())
            .execute()
            .returnResponse();
    }

    @When("^\"([^\"]*)\" asks for a token for attachment \"([^\"]*)\"$")
    public void postDownload(String username, String attachmentId) throws Throwable {
        String blobId = blobIdByAttachmentId.get(attachmentId);
        AccessToken accessToken = userStepdefs.authenticate(username);
        response = Request.Post(baseUri(mainStepdefs.jmapServer).setPath("/download/" + blobId).build())
                .addHeader("Authorization", accessToken.asString())
                .execute()
                .returnResponse();
    }

    @When("^\"([^\"]*)\" downloads \"([^\"]*)\" with \"([^\"]*)\" name$")
    public void downloadsWithName(String username, String attachmentId, String name) throws Exception {
        String blobId = blobIdByAttachmentId.get(attachmentId);
        URIBuilder uriBuilder = baseUri(mainStepdefs.jmapServer).setPath("/download/" + blobId + "/" + name);
        response = authenticatedDownloadRequest(uriBuilder, blobId, username)
                .execute()
                .returnResponse();
    }

    @When("^\"(?:[^\"]*)\" downloads \"([^\"]*)\" with an expired token$")
    public void getDownloadWithExpiredToken(String attachmentId) throws Exception {
        String blobId = blobIdByAttachmentId.get(attachmentId);
        response = Request.Get(baseUri(mainStepdefs.jmapServer).setPath("/download/" + blobId)
                .addParameter("access_token", EXPIRED_ATTACHMENT_TOKEN)
                .build())
            .execute()
            .returnResponse();
    }

    @When("^\"([^\"]*)\" delete mailbox \"([^\"]*)\"$")
    public void deleteMailboxButNotAttachment(String username, String mailboxName) {
        mainStepdefs.mailboxProbe.deleteMailbox(MailboxConstants.USER_NAMESPACE, username, mailboxName);
    }

    @Then("^the user should be authorized$")
    public void httpStatusDifferentFromUnauthorized() {
        assertThat(response.getStatusLine().getStatusCode()).isIn(200, 404);
    }

    @Then("^\"([^\"]*)\" be authorized$")
    public void httpStatusDifferentFromUnauthorized(String username) {
        userStepdefs.execWithUser(username, this::httpStatusDifferentFromUnauthorized);
    }

    @Then("^the user should not be authorized$")
    public void httpUnauthorizedStatus() {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(401);
    }

    @Then("^\"([^\"]*)\" should not be authorized$")
    public void httpUnauthorizedStatus(String username) {
        userStepdefs.execWithUser(username, this::httpUnauthorizedStatus);
    }

    @Then("^the user should receive a bad request response$")
    public void httpBadRequestStatus() {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(400);
    }

    @Then("^\"([^\"]*)\" should receive a bad request response$")
    public void httpBadRequestStatus(String username) {
        userStepdefs.execWithUser(username, this::httpBadRequestStatus);
    }

    @Then("^(?:he|she|the user) can read that blob")
    public void httpOkStatusAndExpectedContent() throws IOException {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
        assertThat(IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8)).isNotEmpty();
    }

    @Then("^the user should receive a not found response$")
    public void httpNotFoundStatus() {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(404);
    }

    @Then("^\"([^\"]*)\" should receive a not found response$")
    public void httpNotFoundStatus(String username) {
        userStepdefs.execWithUser(username, this::httpNotFoundStatus);
    }

    @Then("^the user should receive an attachment access token$")
    public void accessTokenResponse() throws Throwable {
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
        assertThat(response.getHeaders("Content-Type")).extracting(Header::getValue).containsExactly("text/plain");
        assertThat(IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8)).isNotEmpty();
    }

    @Then("^\"([^\"]*)\" should receive an attachment access token$")
    public void accessTokenResponse(String username) {
        userStepdefs.execWithUser(username, this::accessTokenResponse);
    }

    @Then("^the attachment is named \"([^\"]*)\"$")
    public void assertContentDisposition(String name) {
        if (!CharMatcher.ascii().matchesAllOf(name)) {
            assertEncodedFilenameMatches(name);
        } else {
            assertThat(response.getFirstHeader("Content-Disposition").getValue()).isEqualTo("attachment; filename=\"" + name + "\"");
        }
    }

    @Then("^the blob size is (\\d+)$")
    public void assertContentLength(int size) {
        assertThat(response.getFirstHeader("Content-Length").getValue()).isEqualTo(String.valueOf(size));
    }

    @Then("^CORS headers are positioned$")
    public void assertCorsHeader() {
        assertThat(response.getFirstHeader("Access-Control-Allow-Origin").getValue()).isEqualTo("*");
        assertThat(response.getFirstHeader("Access-Control-Allow-Methods").getValue()).isEqualTo("GET, POST, DELETE, PUT");
        assertThat(response.getFirstHeader("Access-Control-Allow-Headers").getValue()).isEqualTo("Content-Type, Authorization, Accept");
        assertThat(response.getFirstHeader("Access-Control-Max-Age").getValue()).isEqualTo("86400");
    }

    @Then("^the Content-Type is \"([^\"]*)\"$")
    public void assertContentType(String contentType) {
        assertThat(response.getFirstHeader("Content-Type").getValue()).isEqualTo(contentType);
    }

    private void assertEncodedFilenameMatches(String name) {
        String contentDispositionHeader = response.getHeaders("Content-Disposition")[0].toString();
        assertThat(contentDispositionHeader).startsWith(UTF8_CONTENT_DIPOSITION_START);

        String expectedFilename = decode(extractFilename(contentDispositionHeader));
        assertThat(name).isEqualTo(expectedFilename);
    }

    private String extractFilename(String contentDispositionHeader) {
        return contentDispositionHeader.substring(UTF8_CONTENT_DIPOSITION_START.length(),
                contentDispositionHeader.length() - 1);
    }

    private String decode(String name) {
        return DecoderUtil.decodeEncodedWords(name, StandardCharsets.UTF_8);
    }
}
