_Note that this project depends on internal Symphony infrastructure (repository.symphony.com), and therefore it can only be built by Symphony LLC employees/partners._

# Integrations Core Documentation

This document provides a brief overview of Integration Core components and how to build them from scratch.

# Overview

Integration Core is responsible to manage active integrations and provides key services to allow third party
services the ability to post messages into a configurable set of streams.

The key services provided to the registered integrations are:

* Authentication proxy - each integration should be configured with credentials, but the implementation never needs
to deal with them.  Once bootstrapped, the integration can use integration bridge services as if it's unauthenticated.
The bridge itself proxies those services to the cloud with the proper authentication.
* Send messages to a stream
* Read and write configuration information to the cloud configuration services
* Read user information to the cloud user services
* Health check

# Build instructions for the Java developer

### What you’ll build
You’ll build a simple java web application that provides the key services described above.
It also builds the other integration modules we currently have, making them available to parse requests from any of the supported and configured integrations.

If you add a new integration, to get it up and running you also need to add it to the integration-web [pom.xml](integration-web/pom.xml)

### What you’ll need
* JDK 1.7
* Maven 3.0.5+

### Build with maven
Integration Core is compatible with Apache Maven 3.0.5 or above. If you don’t already have Maven installed you can follow the instructions at maven.apache.org.

To start from scratch, do the following:

1. Clone the source repository using Git: `git clone https://github.com/SymphonyOSF/App-Integrations-Core.git`
2. cd into _App-Integrations-Core_
3. Build using maven: `mvn clean install`

Notes: If you have no access to Symphony Artifactory you should build all other modules first, starting with the Commons module and then the other Integration modules:

1. [_App-Integrations-Commons_](https://github.com/symphonyoss/App-Integrations-Commons) 

2. [_App-Integrations-Universal_](https://github.com/symphonyoss/App-Integrations-Universal)

3. [_App-Integrations-Github_](https://github.com/symphonyoss/App-Integrations-Github)

4. [_App-Integrations-Jira_](https://github.com/symphonyoss/App-Integrations-Jira)

5. [_App-Integrations-Salesforce_](https://github.com/symphonyoss/App-Integrations-Salesforce)

6. [_App-Integrations-Trello_](https://github.com/symphonyoss/App-Integrations-Trello)

7. [_App-Integrations-Zapier_](https://github.com/symphonyoss/App-Integrations-Zapier)

# Configuring the project
Here are the initial steps to get your project configured to run using the Intellij IDEA IDE.

1. Import this project into your IDE as a ``maven`` project and be sure to choose JDK 1.7 to run it with.
2. Import any other Integration projects the same way as above (like, let's say, App-Integrations-Github or App-Integrations-Commons), but those are not required.
3. Copy [this file](docs/configuration/idea/IntegrationBridgeApplication.xml) to your "project source folder"/.idea/runConfigurations (feel free to create the last folder if you don't have it yet).
4. Go to ``Run > Edit Configurations...`` and select check the one called "IntegrationBridgeApplication"
5. Check that the referenced folders do exist, they should all be pointing to ``docs/configuration/boot/`` folders, to exemplify the structure you need
6. Obtain valid user certificates to your POD and copy those to ``docs/configuration/boot/certs``, you'll need one for each integration
7. Configure valid addresses to connect the application to on the file [application.yaml](docs/configuration/boot/application.yaml)
8. Run ``IntegrationBridgeApplication`` from the "Run" menu and we're done!