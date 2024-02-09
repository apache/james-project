//adds the profiles active in the reactor as buildScan tags
session.getRequest().getActiveProfiles().stream().flatMap {it.getActiveProfiles()}.forEach { it->
    buildScan.tag("P"+it.id)
}
buildScan.value('parallel', session.parallel as String)
buildScan.tag("T"+session.getRequest().getDegreeOfConcurrency())
