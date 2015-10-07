package org.apache.james.dnsservice.dnsjava;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.Name;
import org.xbill.DNS.SetResponse;
import org.xbill.DNS.Zone;

public class ZoneCacheLookupRecordsAnswer implements Answer<SetResponse> {

    private static final Logger LOG = LoggerFactory.getLogger(ZoneCacheLookupRecordsAnswer.class);

    private final Zone zone;

    public ZoneCacheLookupRecordsAnswer(Zone zone) {
        this.zone = zone;
    }

    @Override
    public SetResponse answer(InvocationOnMock invocation) throws Throwable {
        Object[] arguments = invocation.getArguments();
        LOG.info("Cache.lookupRecords {}, {}, {}", arguments[0], arguments[1], arguments[2]);
        assert arguments[0] instanceof Name;
        assert arguments[1] instanceof Integer;
        return zone.findRecords((Name) arguments[0], (Integer) arguments[1]);
    }
}
