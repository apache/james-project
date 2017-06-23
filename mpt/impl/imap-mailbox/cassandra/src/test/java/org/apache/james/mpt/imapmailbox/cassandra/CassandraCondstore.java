package org.apache.james.mpt.imapmailbox.cassandra;

import org.apache.james.mpt.host.JamesImapHostSystem;
import org.apache.james.mpt.imapmailbox.suite.Condstore;
import org.junit.After;
import org.junit.Before;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class CassandraCondstore extends Condstore {

    private JamesImapHostSystem system;

    @Before
    public void setUp() throws Exception {
        Injector injector = Guice.createInjector(new CassandraMailboxTestModule());
        system = injector.getInstance(JamesImapHostSystem.class);
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
