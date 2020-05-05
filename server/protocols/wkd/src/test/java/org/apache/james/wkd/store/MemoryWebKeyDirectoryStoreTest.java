package org.apache.james.wkd.store;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.wkd.crypto.WebKeyDirectorySubmissionAddressKeyPairManager;
import org.junit.jupiter.api.Test;

class MemoryWebKeyDirectoryStoreTest {

    @Test
    void test() throws DomainListException {
        DomainList domainList = mock(DomainList.class);
        when(domainList.getDefaultDomain()).thenReturn(Domain.of("example.org"));
        WebKeyDirectorySubmissionAddressKeyPairManager webKeyDirectorySubmissionAddressKeyPairManager = new WebKeyDirectorySubmissionAddressKeyPairManager(
            domainList, null, null);
        WebKeyDirectoryStore webKeyDirectoryStore = new MemoryWebKeyDirectoryStore(webKeyDirectorySubmissionAddressKeyPairManager);
        // zbase32(sha1(submission-address@example.org)) =
        // ez5ttrptyoa3fqrk3649ns5s7ksxjsro
        assertTrue(webKeyDirectoryStore.containsKey("ez5ttrptyoa3fqrk3649ns5s7ksxjsro"));
        assertNotNull(webKeyDirectoryStore.get("ez5ttrptyoa3fqrk3649ns5s7ksxjsro"));
    }

}
