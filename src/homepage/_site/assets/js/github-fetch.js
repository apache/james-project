let repositoryInfo = {};
let allContributors = [];
const repositoryUrl = "https://api.github.com/repos/apache/james-project";

function getAllCollaborators(callback) {
  if (allContributors.length > 0) {
    callback(allContributors);
  } else {
    $.get(repositoryInfo.contributors_url, function(contributors) {
      allContributors = contributors;
      callback(contributors);
    });
  }
}

function getListContributorDom(contributors) {
  let list = "";
  contributors.sort(function(a, b){
    if(a.login.toLowerCase() < b.login.toLowerCase()) return -1;
    if(a.login.toLowerCase() > b.login.toLowerCase()) return 1;
    return 0;
  });
  for (contributor of contributors) {
    list += `<li class="contributor-card"><a href="` + contributor.html_url + `" alt="` + contributor.login + ` github account"><span class="image contributor-avatar"><img src="` + contributor.avatar_url + `"></span><span class="icon fa-github"></span>` + contributor.login + `</a></li>`;
  }
  return list;
}

function getTotalCommit(callback) {
  $.get("https://api.github.com/repos/apache/james-project/stats/commit_activity", function(data) {
    let total = 0;
    for(week of data) {
      total += week.total;
    }
    callback(total);
  });
}

$(document).ready(function () {
  $.get(repositoryUrl, function(repository) {
    repositoryInfo = repository;
    getAllCollaborators(function(collabs) {
      $('#contributor-num').append(collabs.length);
      let contributors = getListContributorDom(collabs);
      $('#contributor-list').append(contributors);
    });
    getTotalCommit(function(commits) {
      $('#commit-num').append(commits);
    });
    $('#fork-num').append(repositoryInfo.forks_count);
    $('#star-num').append(repositoryInfo.watchers_count);
  });
});
