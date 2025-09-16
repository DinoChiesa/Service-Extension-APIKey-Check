## Service Extension for External Authorization check

This is a Service Extension implemented in Java, applied to an external load
balancer, to allow or deny access to a Cloud Run service, based on the contents
of the Authorization header passed in the request.

## Disclaimer

This example is not an official Google product, nor is it part of an
official Google product.


### Background

There is a new feature that allows people to write their own [service
extensions](https://cloud.google.com/service-extensions/docs/overview) for use
with the Google Cloud Load Balancers. These are GRPC servers that the load
balancer will call "in line" while handling a request. The server can examine
the request and perform various logic: authorization check, logging, redirects,
and so on. Consult the documentation for dull details.

A customer came to me and asked, "What's the simplest way to get APIKey
authentication on a small set of Cloud Run services?" And I think this service
extensions approach is probably that.

Of course there are API Management systems - Google has API Gateway and of
course Apigee... but those are a bit more involved, and maybe more than what
some people want?

The service extension idea provides a simpler approach, though it comes with the
cost of some manage-it-yourself infrastructure.

### This example

This example has these elements:

- a NodeJS app, deployed to Cloud Run, that just returns information.

- a serverless NEG and HTTPS proxy that connects to that Cloud Run service

- an authz extension, implemented in Java, also deployed to Cloud Run, that checks API keys.

- an API key store accessible to the Java app. Currently the keys are just
  hardcoded into the Java app, but it would be straighforward to implement an
  external keystore in a Google Sheet or something more elaborate.

The NodeJS app is very simple.  Not very interesting.
The interesting parts come with the Java authorization extension.
Examine the code to see what it does.

The documentation for Service Extensions states:

> Callouts run as general-purpose gRPC servers on user-managed compute VMs and
> Google Kubernetes Engine Pods on Google Cloud, multicloud, or on-premises
> environments.

In this case, though, the authorization extension is loaded as a Cloud Run service.
It doesn't require GKE or VMs or anything elaborate.


## Building it: Prerequisites

To build this, you will need:

- JDK v21 or later
- maven 3.9 or later
- gcloud command line utility
- a Google Cloud project
- plenty of rights in the project

## Building it: the process

First, edit the `env.sh` file and set your own environment variables.

Then, follow the steps in order:
```sh
0-set-project-and-enable-apis.sh
1-enable-cloud-build-roles.sh
2-deploy-cloud-run-greetings-service.sh
3-configure-network-elements.sh
4a-check-certs-provisioning-status.sh
4b-check-access.sh
5-build-image-for-authz-extension.sh
6-deploy-authz-extension-from-image.sh
7-configure-authz-neg-and-backend.sh
8-configure-authz-extension.sh
9a-import-authz-policy.sh
9b-delete-authz-policy.sh
```

You will want to run each one of those, one at a time.
In particular, don't continue until after step 4a tells you the certificate provisioning is completed.

After you run step 9a, you should no longer be able to invoke the service without an API key.

The list of Hard-coded good API keys is:
- `44a39dc0-da72-42f3-8d8d-d6d01378fe4b`
- `0b919f1d-e113-4d08-976c-a2e2d73f412c`

To pass one of them:

```sh
curl -i -H "Authorization: APIKey <Insert-APIKEY-here>" https://xx.yy.zz.nip.io
```

Remove the authz extension with script 9b.
Wait a few moments and again you should be able to invoke the
endpoint again with no API key.

Restore the APIkey enforcement again with script 9a.

## Questions

- What if I want to use multiple backend systems behind this Authorization Extension?

  Not sure. I think you ought to be able to have multiple forwarding rules in the authz policy.
  See script 9a.

- Would it be possible to implement the Authorization Extension in .NET?

  In theory, yes. But I did not find a single ext\_proc example using .NET. There is a 5-year old
  project that showed how to build the GRPC libraries for .NET, but that is only halfway to
  building an ext\_proc extension. If anyone has an example here, let me know.

- Why not just implement this in golang?

  Sure!  Have at it.  Go has good grpc support, and would work just fine.

- or python?!

  Sure!  Python too. It also has good grpc support, and would work just fine.


## Other Resources

- [a Github repo with some basic samples](https://github.com/GoogleCloudPlatform/service-extensions/) - in Python, Go, and Java
- [a Medium article](https://medium.com/google-cloud/serverless-application-authorization-using-google-cloud-load-balancer-service-extensions-39ca3d1ad84a) by a Googler showing the basics.


## Support

This callout is open-source software, and is not a supported part of Google Cloud.  If
you need assistance, you can try inquiring on [the Google Cloud Community forum
dedicated to Apigee](https://goo.gle/apigee-community) There is no service-level
guarantee for responses to inquiries posted to that site.

## License

This material is [Copyright © 2025 Google LLC](./NOTICE).
and is licensed under the [Apache 2.0 License](LICENSE).

## Bugs

- There is no teardown script
