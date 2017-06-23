package org.apache.james.mpt.imapmailbox.lucenesearch;

import org.apache.james.mpt.host.JamesImapHostSystem;
import org.apache.james.mpt.imapmailbox.suite.Condstore;
import org.junit.After;
import org.junit.Before;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class LuceneCondstore extends Condstore {

    private JamesImapHostSystem system;

    @Before
    public void setUp() throws Exception {
        Injector injector = Guice.createInjector(new LuceneSearchMailboxTestModule());
        system = injector.getInstance(JamesImapHostSystem.class);
        system.beforeTest();
        super.setUp();
    }
    
    @Override
    protected JamesImapHostSystem createJamesImapHostSystem() {
        return system;
    }

    @After
    public void tearDown() throws Exception {
        system.afterTest();
    }
    
}
