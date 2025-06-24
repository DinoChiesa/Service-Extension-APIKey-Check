#!/bin/bash
# -*- mode:shell-script; coding:utf-8; -*-

source ./lib/utils.sh

check_and_maybe_create_sa() {
  local short_sa project sa_email
  short_sa="$1"
  project="$2"
  sa_email="${short_sa}@${project}.iam.gserviceaccount.com"
  printf "Checking Service account (%s)...\n" "${sa_email}"
  echo "gcloud iam service-accounts describe ${sa_email} "
  if gcloud iam service-accounts describe "${sa_email}" --quiet >>/dev/null 2>&1; then
    printf "That service account already exists.\n"
  else
    printf "Creating Service account (%s)...\n" "${sa_email}"
    echo "gcloud iam service-accounts create $short_sa --project=$project --quiet"
    gcloud iam service-accounts create "$short_sa" --project "$project" --quiet 2>&1
    printf "There can be errors if we try to use the SA immediately, so we need to sleep a bit...\n"
    sleep 5
  fi
}

# ====================================================================

check_shell_variables REPOSITORY_PROJECT CLOUDRUN_AUTHZ_SERVICE_NAME \
  CLOUDRUN_SERVICE_REGION CLOUDRUN_PROJECT_ID \
  AUTHZ_SHORT_SA

check_required_commands gcloud
sa_email="$AUTHZ_SHORT_SA@${CLOUDRUN_PROJECT_ID}.iam.gserviceaccount.com"

if [[ -z "$SHEET_ID" ]]; then
  printf "\nYou will need to manually create a Google Sheet with the API keys.\n"
  printf "Name the worksheet \"Keys\", and set the data this way:\n"
  printf "(Use row 1 for headers)\n"
  printf " column 1: key value\n"
  printf " column 2: path pattern\n"
  printf " column 3: verb set (comma separated)\n"
  printf "\nExample:\n\n"
  printf "[row1] Key                        Path pattern    Verb set\n"
  printf "[row2] 10b59e3b-f237-41ab-be96    /foo/bar        GET\n"
  printf "[row3] 2741ffbe-2280-4125-ad07    /status         GET,POST\n"
  printf "[row4] 42ff9ade-3852-457e-b424    /orders/*       GET\n"
  printf "[row4] 42ff9ade-3852-457e-b424    /status         GET\n"
  printf "[rowN] ...\n\n"
  printf "Save the sheet, and share it with: %s\n\n" "$sa_email"
  printf "Finally, set the unique sheet ID into the SHEET_ID environment variable.\n"
  printf "   export SHEET_ID=xxxxyxyyyyyyyxyx\n\n"
  printf "And then re-run this script.\n\n"
  exit
fi

#SHEET_ID=1eeluePPX1b9um33ZmEUTLKXRGoSjqISCNkWaZYH_bjQ
check_and_maybe_create_sa "$AUTHZ_SHORT_SA" "${CLOUDRUN_PROJECT_ID}"

printf "\nDeploy the container image to Cloud Run as '%s'...\n" "${CLOUDRUN_AUTHZ_SERVICE_NAME}"
image_name_and_tag="service-extension-authz-container:20250622"
gcloud run deploy "${CLOUDRUN_AUTHZ_SERVICE_NAME}" \
  --project "${CLOUDRUN_PROJECT_ID}" \
  --set-env-vars "VERBOSE=false" \
  --set-env-vars "SHEET_ID=${SHEET_ID}" \
  --region "${CLOUDRUN_SERVICE_REGION}" \
  --service-account "${sa_email}" \
  --cpu 1 \
  --memory '512Mi' \
  --min-instances 1 \
  --max-instances 1 \
  --no-invoker-iam-check \
  --use-http2 \
  --image "gcr.io/${REPOSITORY_PROJECT}/cloud-builds-submit/${image_name_and_tag}"

printf "\nOk.\n"
