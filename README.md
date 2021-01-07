# OAuth 2.0 with Keycloak and Clojure (WIP!)

![Authorization Code Flow image](images/authorization_code_flow.png)

The purpose of this project is to showcase the use of Keycloak as an authorization server. We will be using Clojure for building the authorization client and other protected resources.
As time goes on, other features will be added and documented. The possible features can be seen in the listing below:

* Protecting resources with JWT tokens and added security with PKCE
* Redis for caching
* UI with Clojurescript (Hiccup, Reagent & maybe Re-frame as well ;))
* CI/CD (not sure about the technologies yet...)
* Tests

## Keycloak

Keycloak is used as our authorization server and access management solution.

I'll be running a containerized keycloak, using the docker-compose tool.  
To boot the container, run the command:
```
$ docker-compose -f keycloak-postgres.yml up
```
Keycloak will be available at this address: `http://localhost:8080/auth`  
You can log in as administrator with these credentials: `admin / admin`   
The following command will allow us to import the preconfigured keycloak testing environment:
```
$ docker exec -it mykeycloak /opt/jboss/keycloak/bin/standalone.sh \
-Djboss.socket.binding.port-offset=100 \
-Dkeycloak.migration.action=import \
-Dkeycloak.migration.provider=singleFile \
-Dkeycloak.migration.file=/opt/jboss/keycloak/imports/my_realm.json
```
The following elements will be generated for us automatically: 
* Test realm -> `Sso-test`
* Authorization client -> `billingApp`
* Token checker client -> `tokenChecker` 
* Test user with credentials -> `bob / return0`
* New client scope -> `getBillingService`   
The `getBillingService` scope is required in the token when requesting services from the protected resource server. 

If you choose to create your own test keycloak environment, use the following configuration for your client:
* Client protocol -> `openid-connect`
* Access type -> `confidential`
* Root URL -> `http://localhost:3000`

To list the Keycloak endpoints in use in our authorization client, load your test realm page and click on the endpoints link. You should get a similar listing as in the pic below:

![Openid-configuration image](images/openid-configuration.png)

Lastly add a new user for login purposes.

## Authorization Client (Clojure)
The Clojure projects are managed with the Clojure CLI tool. The `deps.edn` file holds the configuration and the needed dependencies.  
To run the service, cd into the `clj-auth-service` directory and execute the command:
```
$ clj -M -m core.sso-clojure
```
The authorization service will be running on port `3000`.

The `Services` link on the landing page is mapped to a handler, that fetches data from the `Billing` service which is protected.   
We need to be logged in and have a valid token with the right scope in order to successfully request the services.

## Billing Service Client (Clojure)
To run the service, CD into the `billing-service` directory and run the command:
```
$ clj -M -m core.billing-service
```
The protected resource server will be running on port `4000`.

## License

* [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
