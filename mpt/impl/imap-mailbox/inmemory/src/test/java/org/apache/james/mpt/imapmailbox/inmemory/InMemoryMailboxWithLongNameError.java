package org.apache.james.mpt.imapmailbox.inmemory;

import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.suite.MailboxWithLongNameError;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;

import com.google.inject.Guice;
import com.google.inject.Injector;

@Ignore("why ?")
public class InMemoryMailboxWithLongNameError extends MailboxWithLongNameError {

    private ImapHostSystem system;

    @Before
    public void setUp() throws Exception {
        Injector injector = Guice.createInjector(new InMemoryMailboxTestModule());
        system = injector.getInstance(ImapHostSystem.class);
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
    
}
