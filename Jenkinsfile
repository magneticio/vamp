pipeline {
  agent any

  options {
    buildDiscarder logRotator(daysToKeepStr: '30', numToKeepStr: '30')
    disableConcurrentBuilds()
    ansiColor('xterm')
  }

  stages {
    stage('Build') {

      steps {
        build job: "../vamp-docker-images/" + getBranch().replaceAll("/", "%2F"),
          parameters: [
            [$class: 'StringParameterValue', name: 'VAMP_GIT_ROOT', value: 'git@github.com:' + env.JOB_NAME.split("/").first()],
            [$class: 'StringParameterValue', name: 'VAMP_GIT_BRANCH', value: env.BRANCH_NAME]
          ],
          wait: true
      }
    }
  }
}


import groovy.json.JsonSlurperClassic

def getBranch() {
  def req = httpRequest consoleLogResponseBody: true, httpMode: 'GET', acceptType: 'APPLICATION_JSON', url: "https://api.github.com/repos/${env.JOB_NAME.split("/").first()}/vamp-docker-images/branches", validResponseCodes: '200'

  def cur = env.JOB_NAME.split("/").last()
  def data = new JsonSlurperClassic().parseText(req.content)
  def branches = []
  for (item in data) {
    branches << item.name
  }

  if (branches.grep(cur).size() > 0) {
    return cur
  }

  return 'master'
}
