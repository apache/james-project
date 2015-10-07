package org.apache.james.queue.jms;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.policy.PolicyEntry;
import org.apache.activemq.broker.region.policy.PolicyMap;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.util.Arrays;

public class JMSMailQueueTest extends AbstractJMSMailQueueTest {

    private JMSMailQueue queue;
    private static BrokerService broker;

    @BeforeClass
    public static void setUpBroker() throws Exception {
        broker = createBroker();
        broker.start();
    }

    @AfterClass
    public static void tearDownBroker() throws Exception {
        if (broker != null) {
            broker.stop();
        }
    }

    protected static BrokerService createBroker() throws Exception {
        BrokerService aBroker = new BrokerService();
        aBroker.setPersistent(false);
        aBroker.setUseJmx(false);
        aBroker.addConnector("tcp://127.0.0.1:61616");

        // Enable priority support
        PolicyMap pMap = new PolicyMap();
        PolicyEntry entry = new PolicyEntry();
        entry.setPrioritizedMessages(true);
        entry.setQueue(QUEUE_NAME);
        pMap.setPolicyEntries(Arrays.asList(entry));
        aBroker.setDestinationPolicy(pMap);

        return aBroker;
    }

    @Override
    public JMSMailQueue getQueue() {
        return queue;
    }

    @Override
    public void setQueue(JMSMailQueue queue) {
        this.queue = queue;
    }
}
