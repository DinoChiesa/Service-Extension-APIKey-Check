#!/bin/bash
# -*- mode:shell-script; coding:utf-8; -*-

source ./lib/utils.sh

check_shell_variables SAMPLE_NAME_ROOT CLOUDRUN_PROJECT_ID CLOUDRUN_AUTHZ_SERVICE_NAME
check_required_commands gcloud

printf "\nThis script deletes the authz-policy\n"
printf "in the project '%s'.\n" "$CLOUDRUN_PROJECT_ID"

authz_policy_name="authz-extnsn-policy-1"
printf "\nDeleting the authz extension policy...\n"
gcloud beta network-security authz-policies delete "${authz_policy_name}" \
  --project "${CLOUDRUN_PROJECT_ID}" \
  --location "global"

printf "\nOK.\nThe update will take a few moments to become effective...\n\n"
