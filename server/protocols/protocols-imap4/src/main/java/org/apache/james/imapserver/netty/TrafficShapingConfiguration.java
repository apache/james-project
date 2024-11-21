package org.apache.james.imapserver.netty;

import org.apache.commons.configuration2.Configuration;

import io.netty.handler.traffic.ChannelTrafficShapingHandler;

public record TrafficShapingConfiguration(long writeLimit, long readLimit, long checkInterval, long maxTime) {
    static TrafficShapingConfiguration from(Configuration configuration) {
        return new TrafficShapingConfiguration(
            configuration.getLong("writeTrafficPerSecond", 0),
            configuration.getLong("readTrafficPerSecond", 0),
            configuration.getLong("checkInterval", 30),
            configuration.getLong("maxDelays", 30));
    }

    public ChannelTrafficShapingHandler newHandler(){
        return new ChannelTrafficShapingHandler(writeLimit, readLimit, checkInterval, maxTime);
    }
}
