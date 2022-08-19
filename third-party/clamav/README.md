# James' extensions for ClamAV

This module is for developing and delivering extensions to James for the [ClamAV](https://www.clamav.net/) (the antivirus engine) integration.

Currently, this module provides `ClamAVScan` mailet that talks directly with ClamAV via unix socket to scan virus for every
incoming mail. Upon having virus, mail will be redirected to `virus` processor with configurable behavior for further processing.

E.g:
```xml
    <processor state="local-delivery" enableJmx="true">
        <mailet match="All" class="org.apache.james.clamav.ClamAVScan">
            <host>clamav</host>
            <port>3310</port>
            <onMailetException>ignore</onMailetException>
        </mailet>
        <!-- If infected go to virus processor -->
        <mailet match="HasMailAttributeWithValue=org.apache.james.infected, true" class="ToProcessor">
            <processor>virus</processor>
        </mailet>
    </processor>
    
    <processor state="virus" enableJmx="false">
        <mailet match="All" class="ToRepository">
            <repositoryPath>cassandra://var/mail/virus/</repositoryPath>
        </mailet>
    </processor>
```

To run James with this ClamAV integration, please use James's jar extension mechanism.
We also provide a sample [docker-compose.yml](docker-compose.yml) on how to setup the James <-> ClamAV integration.