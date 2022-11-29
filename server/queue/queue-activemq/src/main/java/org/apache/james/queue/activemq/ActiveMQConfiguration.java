package org.apache.james.queue.activemq;

import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.apache.james.queue.activemq.metric.ActiveMQMetricConfiguration;

public class ActiveMQConfiguration {

    private final ActiveMQMetricConfiguration metricConfiguration;

    public static ActiveMQConfiguration getDefault() {
        return from(new BaseConfiguration());
    }

    public static ActiveMQConfiguration from(Configuration configuration) {
        return new ActiveMQConfiguration(ActiveMQMetricConfiguration.from(configuration));
    }

    private ActiveMQConfiguration(ActiveMQMetricConfiguration metricConfiguration) {
        this.metricConfiguration = metricConfiguration;
    }

    public ActiveMQMetricConfiguration getMetricConfiguration() {
        return metricConfiguration;
    }
}
