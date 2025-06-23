#!/bin/bash
# -*- mode:shell-script; coding:utf-8; -*-

source ./lib/utils.sh

check_shell_variables REPOSITORY_PROJECT
check_required_commands mvn java

cd extension
MAVEN_OPTS="--enable-native-access=ALL-UNNAMED" mvn clean package jib:build

