# [Jenkins nvm Plugin (nvm-wrapper)](https://wiki.jenkins-ci.org/display/JENKINS/Nvm+Wrapper+Plugin)

[![Build Status](https://travis-ci.org/jSherz/nvm-wrapper-plugin.svg?branch=master)](https://travis-ci.org/jSherz/nvm-wrapper-plugin)

A Java / Groovy version of [Jenkins nvm plugin](https://github.com/codevise/jenkins-nvm-plugin). It does not require Ruby to be installed.

## Usage

- See [the Nvm Wrapper Plugin on jenkins-ci.org](https://wiki.jenkins-ci.org/display/JENKINS/Nvm+Wrapper+Plugin).

- For a pipeline job:

    ```groovy
    nvm(version: 'v6.9.4') {
      // build steps here
      sh 'env'
    }
    ```

## Build

* This project is built with Gradle 3.3 and Java 1.8.

* `gradle jpi`

    Build the Jenkins plugin file, which can then be found in the build directory. The file will currently end in ".hpi".

* `gradle install`

    Build the Jenkins plugin and install it into your local Maven repository.

* `gradle uploadArchives` (or `gradle deploy`)

    Deploy your plugin to the Jenkins Maven repository to be included in the Update Center.

* `gradle server`

    Run a local jenkins to test

## Features

* Installs [nvm](https://github.com/creationix/nvm)
* Installs the configured version of Node.
* Injects the installed version of Node into the build environment.
* Compatible with Jenkins pipeline / workflow plugin.

## Acknowledgements

Based on:

* [Jenkins rvm plugin](https://github.com/jenkinsci/rvm-plugin) and
* [Jenkins nvm plugin](https://github.com/codevise/jenkins-nvm-plugin).

## License

Copyright (c) 2016 Tomas Salazar. This software is licensed under the MIT License.

Please fork and improve.
