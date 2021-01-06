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

Keycloak is used as our authorization server.

The following command will get our dockerized keycloak up and running:
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
The command will generate for us a test realm (Sso-test), an authorization client (billingApp) a token checker client (tokenChecker) and finally a test user with these credentials -> `bob / return0`.

If you choose to create your own test keycloak environment, use the following configuration for your client:
* Client protocol -> `openid-connect`
* Access type -> `confidential`
* Root URL -> `http://localhost:3000`

To list the Keycloak endpoints in use in our authorization client, load your test realm page and click on the endpoints link. You should get a similar listing as in the pic below:

![Openid-configuration image](images/openid-configuration.png)

Lastly add a new user for login purposes.

## Authorization Client (Clojure)
The Clojure projects are managed with the Clojure CLI tool. The `deps.edn` file holds the configuration and the needed dependencies.
Cd into the `clj-auth-service` directory and execute this command:
```
$ clj -M -m core.sso-clojure
```
Our authorization service will be running at port `3000`.

## Billing Service Client (Clojure)
To run the app, CD into the `billing-service` directory and run this command:
```
$ clj -M -m core.billing-service
```

The `Services` link on the landing page is mapped to a handler, that fetches data from this service.

## License

* [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
