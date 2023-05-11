package org.apache.james.protocols.lib.netty;

public interface CertificateReloadable {

    void reloadSSLCertificate() throws Exception;
}
