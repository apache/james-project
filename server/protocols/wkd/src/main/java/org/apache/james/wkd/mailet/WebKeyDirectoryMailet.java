package org.apache.james.wkd.mailet;

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.james.domainlist.api.DomainList;
import org.apache.james.wkd.crypto.WebKeyDirectorySubmissionAddressKeyPairManager;
import org.apache.james.wkd.store.WebKeyDirectoryStore;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebKeyDirectoryMailet extends GenericMailet {


    private static final Logger LOGGER = LoggerFactory.getLogger(WebKeyDirectoryMailet.class);

    private DomainList domainList;
    private WebKeyDirectoryStore webKeyDirectoryStore;
    private WebKeyDirectorySubmissionAddressKeyPairManager webKeyDirectorySubmissionAddressKeyPairManager;
    

    @Inject
    public WebKeyDirectoryMailet(DomainList domainList, WebKeyDirectoryStore webKeyDirectoryStore, WebKeyDirectorySubmissionAddressKeyPairManager webKeyDirectorySubmissionAddressKeyPairManager) {
        this.domainList = domainList;
        this.webKeyDirectoryStore = webKeyDirectoryStore;
        this.webKeyDirectorySubmissionAddressKeyPairManager = webKeyDirectorySubmissionAddressKeyPairManager;
    }

    @Override
    public void service(Mail mail) throws MessagingException {

    }

}
