#!/bin/bash
# -*- mode:shell-script; coding:utf-8; -*-

source ./lib/utils.sh

check_shell_variables SAMPLE_NAME_ROOT CLOUDRUN_PROJECT_ID CLOUDRUN_AUTHZ_SERVICE_NAME
check_required_commands gcloud

printf "\nThis script imports an Authz policy, referencing the previously-defined\n"
printf "extension in the project '%s'.\n" "$CLOUDRUN_PROJECT_ID"

SAMPLE_ADDRESS_NAME="${SAMPLE_NAME_ROOT}-ip"
IP=$(gcloud compute addresses describe "${SAMPLE_ADDRESS_NAME}" \
  --project "${CLOUDRUN_PROJECT_ID}" \
  --format="get(address)" \
  --global)

dnsname="${IP}.nip.io"
printf "DNS name: %s\n" "${dnsname}"

authz_ext_name="authz-extnsn-1"
authz_policy_name="authz-extnsn-policy-1"
SAMPLE_FWDRULE_NAME="${SAMPLE_NAME_ROOT}-fwdrule"
cat >/tmp/authz-policy.yaml <<EOF
name: ${authz_policy_name}
target:
  loadBalancingScheme: EXTERNAL_MANAGED
  resources:
    - "https://www.googleapis.com/compute/v1/projects/${CLOUDRUN_PROJECT_ID}/global/forwardingRules/${SAMPLE_FWDRULE_NAME}"
action: CUSTOM
customProvider:
  authzExtension:
    resources:
      - "projects/${CLOUDRUN_PROJECT_ID}/locations/global/authzExtensions/${authz_ext_name}"
EOF

cat "/tmp/authz-policy.yaml"

printf "\nApplying the authz extension policy...\n"
gcloud beta network-security authz-policies import "${authz_policy_name}" \
  --project "${CLOUDRUN_PROJECT_ID}" \
  --source "/tmp/authz-policy.yaml" \
  --location "global"

printf "\nOK.\nThe update will take a few moments to become effective...\n\n"
