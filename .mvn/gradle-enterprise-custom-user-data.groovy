//adds the profiles active in the reactor as buildScan tags
session.getRequest().activeProfiles.stream().forEach { it->
    buildScan.tag("P"+it)
}
buildScan.value('parallel', session.parallel as String)
buildScan.tag("T"+session.getRequest().getDegreeOfConcurrency())
