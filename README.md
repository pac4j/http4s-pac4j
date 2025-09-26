<p align="center">
  <img src="https://pac4j.github.io/pac4j/img/logo-http4s.png" width="300" />
</p>

The `http4s-pac4j` project is an **easy and powerful security library for http4s web applications and web services** which supports authentication and authorization, but also logout and advanced features like session fixation and CSRF protection.
It's based on http4s 0.23 and on the **[pac4j security engine](https://github.com/pac4j/pac4j)**. The library is cross-built for Scala 2.12, 2.13 and 3. It's available under the Apache 2 license.

[**Main concepts and components:**](http://www.pac4j.org/docs/main-concepts-and-components.html)

1) A [**client**](http://www.pac4j.org/docs/clients.html) represents an authentication mechanism. It performs the login process and returns a user profile. An indirect client is for web application authentication while a direct client is for web services authentication:

&#9656; OAuth - SAML - CAS - OpenID Connect - HTTP - Google App Engine - LDAP - SQL - JWT - MongoDB - CouchDB - Kerberos - IP address - Kerberos (SPNEGO) - REST API

2) An [**authorizer**](http://www.pac4j.org/docs/authorizers.html) is meant to check authorizations on the authenticated user profile(s) or on the current web context:

&#9656; Roles / permissions - Anonymous / remember-me / (fully) authenticated - Profile type, attribute -  CORS - CSRF - Security headers - IP address, HTTP method

3) The `SecurityFilterMiddleware` protects an url by checking that the user is authenticated and that the authorizations are valid, according to the clients and authorizers configuration. If the user is not authenticated, it performs authentication for direct clients or starts the login process for indirect clients

4) The `CallbackService` finishes the login process for an indirect client

5) The `LogoutService` logs out the user from the application and triggers the logout at the identity provider level.


## Usage

1) [Add the required dependencies](https://github.com/pac4j/http4s-pac4j/wiki/Dependencies)

2) Define:

  - the [security configuration](https://github.com/pac4j/http4s-pac4j/wiki/Security-configuration)
  - the [callback configuration](https://github.com/pac4j/http4s-pac4j/wiki/Callback-configuration), only for web applications
  - the [logout configuration](https://github.com/pac4j/http4s-pac4j/wiki/Logout-configuration)

3) [Apply security](https://github.com/pac4j/http4s-pac4j/wiki/Apply-security)

4) [Get the authenticated user profiles](https://github.com/pac4j/http4s-pac4j/wiki/Get-the-authenticated-user-profiles)


## Demo

The demo webapp for http4s-pac4j: [http4s-pac4j-demo](https://github.com/pac4j/http4s-pac4j-demo)
is available for tests and implements many authentication mechanisms: Facebook, Twitter, form, basic auth, CAS, SAML,
OpenID Connect, JWT...

## Versions

The latest released version is the [![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.pac4j/http4s-pac4j/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/org.pac4j/http4s-pac4j), available in the [Maven central repository](https://repo.maven.apache.org/maven2).

See the [release notes](https://github.com/pac4j/http4s-pac4j/wiki/Release-Notes).

See the [migration guide](https://github.com/pac4j/http4s-pac4j/wiki/Migration-guide) as well.


## Need help?

You can use the [mailing lists](http://www.pac4j.org/mailing-lists.html) or the [commercial support](http://www.pac4j.org/commercial-support.html).

## Credits

Thanks to https://github.com/hgiddens/http4s-session for the Session implementation
