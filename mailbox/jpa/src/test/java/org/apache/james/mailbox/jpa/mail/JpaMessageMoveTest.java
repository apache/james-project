package org.apache.james.mailbox.jpa.mail;

import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.mailbox.jpa.JPAMailboxFixture;
import org.apache.james.mailbox.store.mail.model.MapperProvider;
import org.apache.james.mailbox.store.mail.model.MessageMoveTest;
import org.junit.After;
import org.junit.Before;

public class JpaMessageMoveTest extends MessageMoveTest {
    
    public static final JpaTestCluster JPA_TEST_CLUSTER = JpaTestCluster.create(JPAMailboxFixture.MAILBOX_PERSISTANCE_CLASSES);

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected MapperProvider createMapperProvider() {
        return new JPAMapperProvider(JPA_TEST_CLUSTER);
    }
    
    @After
    public void cleanUp() {
        JPA_TEST_CLUSTER.clear(JPAMailboxFixture.MAILBOX_TABLE_NAMES);
    }

}
