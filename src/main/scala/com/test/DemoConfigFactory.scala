package com.test

import org.pac4j.core.authorization.authorizer.RequireAnyRoleAuthorizer
import org.pac4j.core.client.Clients
import org.pac4j.core.config.{Config, ConfigFactory}
import org.pac4j.http4s.DefaultHttpActionAdapter
import org.pac4j.saml.client.SAML2Client
import org.pac4j.saml.config.SAML2Configuration

class DemoConfigFactory extends ConfigFactory {
  override def build(parameters: AnyRef*): Config = {
    val cfg = new SAML2Configuration("file:abc.p12",
      "pac4j-demo-passwd",
      "pac4j-demo-passwd",
      "resource:metadata-okta.xml")
    cfg.setMaximumAuthenticationLifetime(3600)
    cfg.setServiceProviderEntityId("http://localhost:8080/callback?client_name=SAML2Client")
    cfg.setServiceProviderMetadataPath("sp-metadata.xml")
    val saml2Client = new SAML2Client(cfg)

    val clients = new Clients("http://localhost:8080/callback", saml2Client)

    val config = new Config(clients)
    //config.addAuthorizer("admin", new RequireAnyRoleAuthorizer[_ <: CommonProfile]("ROLE_ADMIN"))
    //config.addAuthorizer("custom", new CustomAuthorizer)
    config.setHttpActionAdapter(new DefaultHttpActionAdapter())  // <-- Render a nicer page
    config
  }
}
