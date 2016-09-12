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
package org.apache.james.mailbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.List;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxManager.MailboxCapabilities;
import org.apache.james.mailbox.exception.AnnotationException;
import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.mock.MockMailboxManager;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MailboxQuery;
import org.junit.After;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.slf4j.LoggerFactory;
import org.xenei.junit.contract.Contract;
import org.xenei.junit.contract.ContractTest;
import org.xenei.junit.contract.IProducer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Test the {@link StoreMailboxManager} methods that
 * are not covered by the protocol-tester suite.
 * 
 * This class needs to be extended by the different mailbox 
 * implementations which are responsible to setup and 
 * implement the test methods.
 * 
 */
@Contract(MailboxManager.class)
public class MailboxManagerTest<T extends MailboxManager> {
    
    public final static String USER_1 = "USER_1";
    public final static String USER_2 = "USER_2";

    private static final MailboxAnnotationKey PRIVATE_KEY = new MailboxAnnotationKey("/private/comment");
    private static final MailboxAnnotationKey PRIVATE_CHILD_KEY = new MailboxAnnotationKey("/private/comment/user");
    private static final MailboxAnnotationKey PRIVATE_GRANDCHILD_KEY = new MailboxAnnotationKey("/private/comment/user/name");
    private static final MailboxAnnotationKey SHARED_KEY = new MailboxAnnotationKey("/shared/comment");

    private static final MailboxAnnotation PRIVATE_ANNOTATION = MailboxAnnotation.newInstance(PRIVATE_KEY, "My private comment");
    private static final MailboxAnnotation PRIVATE_CHILD_ANNOTATION = MailboxAnnotation.newInstance(PRIVATE_CHILD_KEY, "My private comment");
    private static final MailboxAnnotation PRIVATE_GRANDCHILD_ANNOTATION = MailboxAnnotation.newInstance(PRIVATE_GRANDCHILD_KEY, "My private comment");
    private static final MailboxAnnotation PRIVATE_ANNOTATION_UPDATE = MailboxAnnotation.newInstance(PRIVATE_KEY, "My updated private comment");
    private static final MailboxAnnotation SHARED_ANNOTATION =  MailboxAnnotation.newInstance(SHARED_KEY, "My shared comment");

    private static final List<MailboxAnnotation> ANNOTATIONS = ImmutableList.of(PRIVATE_ANNOTATION, SHARED_ANNOTATION);

    @Rule
    public ExpectedException expected = ExpectedException.none();

    private IProducer<T> producer;
    private MailboxManager mailboxManager;
    private MailboxSession session;

    @Contract.Inject
    public final void setProducer(IProducer<T> producer) throws Exception {
        this.producer = producer;
        this.mailboxManager = new MockMailboxManager(producer.newInstance()).getMockMailboxManager();
    }

    @After
    public void tearDown() throws Exception {
        mailboxManager.logout(session, false);
        mailboxManager.endProcessingRequest(session);

        producer.cleanUp();
    }
    
    @ContractTest
    public void createUser1SystemSessionShouldReturnValidSession() throws UnsupportedEncodingException, MailboxException {
        session = mailboxManager.createSystemSession(USER_1, LoggerFactory.getLogger("Mock"));
        
        assertThat(session.getUser().getUserName()).isEqualTo(USER_1);
    }

    @ContractTest
    public void user1ShouldNotHaveAnInbox() throws UnsupportedEncodingException, MailboxException {
        session = mailboxManager.createSystemSession(USER_1, LoggerFactory.getLogger("Mock"));
        mailboxManager.startProcessingRequest(session);
        
        MailboxPath inbox = MailboxPath.inbox(session);
        assertThat(mailboxManager.mailboxExists(inbox, session)).isFalse();
    }
    
    @ContractTest
    public void user1ShouldBeAbleToCreateInbox() throws MailboxException, UnsupportedEncodingException {
        session = mailboxManager.createSystemSession(USER_1, LoggerFactory.getLogger("Mock"));
        mailboxManager.startProcessingRequest(session);
     
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);
        
        assertThat(mailboxManager.mailboxExists(inbox, session)).isTrue();
    }

    @ContractTest
    public void user1ShouldNotBeAbleToCreateInboxTwice() throws MailboxException, UnsupportedEncodingException {
        expected.expect(MailboxException.class);
        session = mailboxManager.createSystemSession(USER_1, LoggerFactory.getLogger("Mock"));
        mailboxManager.startProcessingRequest(session);
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);
        mailboxManager.createMailbox(inbox, session);
    }

    @ContractTest
    public void user1ShouldNotHaveTestSubmailbox() throws MailboxException, UnsupportedEncodingException {
        session = mailboxManager.createSystemSession(USER_1, LoggerFactory.getLogger("Mock"));
        mailboxManager.startProcessingRequest(session);

        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);
        
        assertThat(mailboxManager.mailboxExists(new MailboxPath(inbox, "INBOX.Test"), session)).isFalse();
    }
    
    @ContractTest
    public void user1ShouldBeAbleToCreateTestSubmailbox() throws MailboxException, UnsupportedEncodingException {
        session = mailboxManager.createSystemSession(USER_1, LoggerFactory.getLogger("Mock"));
        mailboxManager.startProcessingRequest(session);
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);
        
        MailboxPath inboxSubMailbox = new MailboxPath(inbox, "INBOX.Test");
        mailboxManager.createMailbox(inboxSubMailbox, session);
        
        assertThat(mailboxManager.mailboxExists(inboxSubMailbox, session)).isTrue();
    }
    
    @ContractTest
    public void user1ShouldBeAbleToDeleteInbox() throws MailboxException, UnsupportedEncodingException {
        session = mailboxManager.createSystemSession(USER_1, LoggerFactory.getLogger("Mock"));
        mailboxManager.startProcessingRequest(session);
     
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);
        MailboxPath inboxSubMailbox = new MailboxPath(inbox, "INBOX.Test");
        mailboxManager.createMailbox(inboxSubMailbox, session);
        
        mailboxManager.deleteMailbox(inbox, session);
        
        assertThat(mailboxManager.mailboxExists(inbox, session)).isFalse();
        assertThat(mailboxManager.mailboxExists(inboxSubMailbox, session)).isTrue();
    }
    
    @ContractTest
    public void user1ShouldBeAbleToDeleteSubmailbox() throws MailboxException, UnsupportedEncodingException {
        session = mailboxManager.createSystemSession(USER_1, LoggerFactory.getLogger("Mock"));
        mailboxManager.startProcessingRequest(session);
     
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);
        MailboxPath inboxSubMailbox = new MailboxPath(inbox, "INBOX.Test");
        mailboxManager.createMailbox(inboxSubMailbox, session);
        
        mailboxManager.deleteMailbox(inboxSubMailbox, session);
        
        assertThat(mailboxManager.mailboxExists(inbox, session)).isTrue();
        assertThat(mailboxManager.mailboxExists(inboxSubMailbox, session)).isFalse();
    }

    @ContractTest
    public void closingSessionShouldWork() throws BadCredentialsException, MailboxException, UnsupportedEncodingException {
        session = mailboxManager.createSystemSession(USER_1, LoggerFactory.getLogger("Mock"));
        mailboxManager.startProcessingRequest(session);

        mailboxManager.logout(session, false);
        mailboxManager.endProcessingRequest(session);
        
        assertThat(session.isOpen()).isFalse();
    }

    @ContractTest
    public void listShouldReturnMailboxes() throws MailboxException, UnsupportedEncodingException {
        session = mailboxManager.createSystemSession("manager", LoggerFactory.getLogger("testList"));
        mailboxManager.startProcessingRequest(session);
        
        assertThat(mailboxManager.list(session)).hasSize(MockMailboxManager.EXPECTED_MAILBOXES_COUNT);
    }

    @ContractTest
    public void user2ShouldBeAbleToCreateRootlessFolder() throws BadCredentialsException, MailboxException {
        session = mailboxManager.createSystemSession(USER_2, LoggerFactory.getLogger("Test"));
        MailboxPath trash = new MailboxPath(MailboxConstants.USER_NAMESPACE, USER_2, "Trash");
        mailboxManager.createMailbox(trash, session);
        
        assertThat(mailboxManager.mailboxExists(trash, session)).isTrue();
    }
    
    @ContractTest
    public void user2ShouldBeAbleToCreateNestedFoldersWithoutTheirParents() throws BadCredentialsException, MailboxException {
        session = mailboxManager.createSystemSession(USER_2, LoggerFactory.getLogger("Test"));
        MailboxPath nestedFolder = new MailboxPath(MailboxConstants.USER_NAMESPACE, USER_2, "INBOX.testfolder");
        mailboxManager.createMailbox(nestedFolder, session);
        
        assertThat(mailboxManager.mailboxExists(nestedFolder, session)).isTrue();
        mailboxManager.getMailbox(MailboxPath.inbox(session), session).appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), session, false, new Flags());
    }

    @ContractTest
    public void searchShouldNotReturnResultsFromOtherNamespaces() throws Exception {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Namespace));
        session = mailboxManager.createSystemSession(USER_1, LoggerFactory.getLogger("Mock"));
        mailboxManager.createMailbox(new MailboxPath("#namespace", USER_1, "Other"), session);
        mailboxManager.createMailbox(MailboxPath.inbox(session), session);
        List<MailboxMetaData> metaDatas = mailboxManager.search(new MailboxQuery(new MailboxPath("#private", USER_1, ""), "*", '.'), session);
        assertThat(metaDatas).hasSize(1);
        assertThat(metaDatas.get(0).getPath()).isEqualTo(MailboxPath.inbox(session));
    }

    @ContractTest
    public void searchShouldNotReturnResultsFromOtherUsers() throws Exception {
        session = mailboxManager.createSystemSession(USER_1, LoggerFactory.getLogger("Mock"));
        mailboxManager.createMailbox(new MailboxPath("#namespace", USER_2, "Other"), session);
        mailboxManager.createMailbox(MailboxPath.inbox(session), session);
        List<MailboxMetaData> metaDatas = mailboxManager.search(new MailboxQuery(new MailboxPath("#private", USER_1, ""), "*", '.'), session);
        assertThat(metaDatas).hasSize(1);
        assertThat(metaDatas.get(0).getPath()).isEqualTo(MailboxPath.inbox(session));
    }

    @ContractTest
    public void updateAnnotationsShouldUpdateStoredAnnotation() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        session = mailboxManager.createSystemSession(USER_2, LoggerFactory.getLogger("Test"));
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);

        mailboxManager.updateAnnotations(inbox, session, ImmutableList.of(PRIVATE_ANNOTATION));

        mailboxManager.updateAnnotations(inbox, session, ImmutableList.of(PRIVATE_ANNOTATION_UPDATE));
        assertThat(mailboxManager.getAllAnnotations(inbox, session)).containsOnly(PRIVATE_ANNOTATION_UPDATE);
    }

    @ContractTest
    public void updateAnnotationsShouldDeleteAnnotationWithNilValue() throws BadCredentialsException, MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        session = mailboxManager.createSystemSession(USER_2, LoggerFactory.getLogger("Test"));
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);

        mailboxManager.updateAnnotations(inbox, session, ImmutableList.of(PRIVATE_ANNOTATION));

        mailboxManager.updateAnnotations(inbox, session, ImmutableList.of(MailboxAnnotation.nil(PRIVATE_KEY)));
        assertThat(mailboxManager.getAllAnnotations(inbox, session)).isEmpty();
    }

    @ContractTest
    public void updateAnnotationsShouldThrowExceptionIfMailboxDoesNotExist() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        expected.expect(MailboxException.class);
        session = mailboxManager.createSystemSession(USER_2, LoggerFactory.getLogger("Test"));
        MailboxPath inbox = MailboxPath.inbox(session);

        mailboxManager.updateAnnotations(inbox, session, ImmutableList.of(PRIVATE_ANNOTATION));
    }

    @ContractTest
    public void getAnnotationsShouldReturnEmptyForNonStoredAnnotation() throws BadCredentialsException, MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        session = mailboxManager.createSystemSession(USER_2, LoggerFactory.getLogger("Test"));
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);

        assertThat(mailboxManager.getAllAnnotations(inbox, session)).isEmpty();
    }

    @ContractTest
    public void getAllAnnotationsShouldRetrieveStoredAnnotations() throws BadCredentialsException, MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        session = mailboxManager.createSystemSession(USER_2, LoggerFactory.getLogger("Test"));
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);

        mailboxManager.updateAnnotations(inbox, session, ANNOTATIONS);

        assertThat(mailboxManager.getAllAnnotations(inbox, session)).isEqualTo(ANNOTATIONS);
    }

    @ContractTest
    public void getAllAnnotationsShouldThrowExceptionIfMailboxDoesNotExist() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        expected.expect(MailboxException.class);
        session = mailboxManager.createSystemSession(USER_2, LoggerFactory.getLogger("Test"));
        MailboxPath inbox = MailboxPath.inbox(session);

        mailboxManager.getAllAnnotations(inbox, session);
    }

    @ContractTest
    public void getAnnotationsByKeysShouldRetrieveStoresAnnotationsByKeys() throws BadCredentialsException, MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        session = mailboxManager.createSystemSession(USER_2, LoggerFactory.getLogger("Test"));
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);

        mailboxManager.updateAnnotations(inbox, session, ANNOTATIONS);

        assertThat(mailboxManager.getAnnotationsByKeys(inbox, session, ImmutableSet.of(PRIVATE_KEY)))
            .containsOnly(PRIVATE_ANNOTATION);
    }

    @ContractTest
    public void getAnnotationsByKeysShouldThrowExceptionIfMailboxDoesNotExist() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        expected.expect(MailboxException.class);
        session = mailboxManager.createSystemSession(USER_2, LoggerFactory.getLogger("Test"));
        MailboxPath inbox = MailboxPath.inbox(session);

        mailboxManager.getAnnotationsByKeys(inbox, session, ImmutableSet.of(PRIVATE_KEY));
    }

    @ContractTest
    public void getAnnotationsByKeysWithOneDepthShouldRetriveAnnotationsWithOneDepth() throws BadCredentialsException, MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        session = mailboxManager.createSystemSession(USER_2, LoggerFactory.getLogger("Test"));
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);

        mailboxManager.updateAnnotations(inbox, session, ImmutableList.of(PRIVATE_ANNOTATION, PRIVATE_CHILD_ANNOTATION, PRIVATE_GRANDCHILD_ANNOTATION));

        assertThat(mailboxManager.getAnnotationsByKeysWithOneDepth(inbox, session, ImmutableSet.of(PRIVATE_KEY)))
            .contains(PRIVATE_ANNOTATION, PRIVATE_CHILD_ANNOTATION);
    }

    @ContractTest
    public void getAnnotationsByKeysWithAllDepthShouldThrowExceptionWhenMailboxDoesNotExist() throws BadCredentialsException, MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        expected.expect(MailboxException.class);
        session = mailboxManager.createSystemSession(USER_2, LoggerFactory.getLogger("Test"));
        MailboxPath inbox = MailboxPath.inbox(session);

        mailboxManager.getAnnotationsByKeysWithAllDepth(inbox, session, ImmutableSet.of(PRIVATE_KEY));
    }

    @ContractTest
    public void getAnnotationsByKeysWithAllDepthShouldRetriveAnnotationsWithAllDepth() throws BadCredentialsException, MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        session = mailboxManager.createSystemSession(USER_2, LoggerFactory.getLogger("Test"));
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);

        mailboxManager.updateAnnotations(inbox, session, ImmutableList.of(PRIVATE_ANNOTATION, PRIVATE_CHILD_ANNOTATION, PRIVATE_GRANDCHILD_ANNOTATION));

        assertThat(mailboxManager.getAnnotationsByKeysWithAllDepth(inbox, session, ImmutableSet.of(PRIVATE_KEY)))
            .contains(PRIVATE_ANNOTATION, PRIVATE_CHILD_ANNOTATION, PRIVATE_GRANDCHILD_ANNOTATION);
    }

    @ContractTest
    public void updateAnnotationsShouldThrowExceptionIfAnnotationDataIsOverLimitation() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        expected.expect(AnnotationException.class);
        session = mailboxManager.createSystemSession(USER_2, LoggerFactory.getLogger("Test"));
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);

        mailboxManager.updateAnnotations(inbox, session, ImmutableList.of(MailboxAnnotation.newInstance(PRIVATE_KEY, "The limitation of data is less than 30")));
    }

    @ContractTest
    public void shouldUpdateAnnotationWhenRequestCreatesNewAndMailboxIsNotOverLimit() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        session = mailboxManager.createSystemSession(USER_2, LoggerFactory.getLogger("Test"));
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);

        ImmutableList.Builder<MailboxAnnotation> builder = ImmutableList.builder();
        builder.add(MailboxAnnotation.newInstance(new MailboxAnnotationKey("/private/comment1"), "AnyValue"));
        builder.add(MailboxAnnotation.newInstance(new MailboxAnnotationKey("/private/comment2"), "AnyValue"));
        builder.add(MailboxAnnotation.newInstance(new MailboxAnnotationKey("/private/comment3"), "AnyValue"));

        mailboxManager.updateAnnotations(inbox, session, builder.build());
    }

    @ContractTest
    public void updateAnnotationsShouldThrowExceptionIfRequestCreateNewButMailboxIsOverLimit() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        expected.expect(MailboxException.class);
        session = mailboxManager.createSystemSession(USER_2, LoggerFactory.getLogger("Test"));
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);

        ImmutableList.Builder<MailboxAnnotation> builder = ImmutableList.builder();
        builder.add(MailboxAnnotation.newInstance(new MailboxAnnotationKey("/private/comment1"), "AnyValue"));
        builder.add(MailboxAnnotation.newInstance(new MailboxAnnotationKey("/private/comment2"), "AnyValue"));
        builder.add(MailboxAnnotation.newInstance(new MailboxAnnotationKey("/private/comment3"), "AnyValue"));
        builder.add(MailboxAnnotation.newInstance(new MailboxAnnotationKey("/private/comment4"), "AnyValue"));

        mailboxManager.updateAnnotations(inbox, session, builder.build());
    }}
