package org.apache.james.mpt.imapmailbox.cyrus;

import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.GrantRightsOnHost;
import org.apache.james.mpt.imapmailbox.MailboxMessageAppender;
import org.apache.james.mpt.imapmailbox.suite.ACLIntegration;
import org.junit.After;
import org.junit.Before;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class CyrusACLIntegration extends ACLIntegration {

    private ImapHostSystem system;
    private GrantRightsOnHost grantRightsOnHost;
    private MailboxMessageAppender mailboxMessageAppender;

    @Before
    public void setUp() throws Exception {
        Injector injector = Guice.createInjector(new CyrusMailboxTestModule());
        system = injector.getInstance(ImapHostSystem.class);
        grantRightsOnHost = injector.getInstance(GrantRightsOnHost.class);
        mailboxMessageAppender = injector.getInstance(MailboxMessageAppender.class);
        system.beforeTest();
        super.setUp();
    }
    
    @Override
    protected ImapHostSystem createImapHostSystem() {
        return system;
    }

    @After
    public void tearDown() throws Exception {
        system.afterTest();
    }

    @Override
    protected GrantRightsOnHost createGrantRightsOnHost() {
        return grantRightsOnHost;
    }

    @Override
    protected MailboxMessageAppender createMailboxMessageAppender() {
        return mailboxMessageAppender;
    }
    
}
