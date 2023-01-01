/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.security.saml2.provider.service.authentication;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.opensaml.core.config.ConfigurationService;
import org.opensaml.core.xml.config.XMLObjectProviderRegistry;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.NameIDPolicy;
import org.opensaml.saml.saml2.core.impl.AuthnRequestBuilder;
import org.opensaml.saml.saml2.core.impl.IssuerBuilder;
import org.opensaml.saml.saml2.core.impl.NameIDPolicyBuilder;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.saml2.core.OpenSamlInitializationService;
import org.springframework.security.saml2.core.Saml2ParameterNames;
import org.springframework.security.saml2.provider.service.authentication.OpenSamlSigningUtils.QueryParametersPartial;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A {@link Saml2AuthenticationRequestFactory} that generates, signs, and serializes a
 * SAML 2.0 AuthnRequest using OpenSAML 4
 *
 * @author Josh Cummings
 * @since 5.5
 * @deprecated Use
 * {@link org.springframework.security.saml2.provider.service.web.authentication.OpenSaml4AuthenticationRequestResolver}
 * instead
 */
@Deprecated
public final class OpenSaml4AuthenticationRequestFactory implements Saml2AuthenticationRequestFactory {

	static {
		OpenSamlInitializationService.initialize();
	}

	private final AuthnRequestBuilder authnRequestBuilder;

	private final IssuerBuilder issuerBuilder;

	private final NameIDPolicyBuilder nameIdPolicyBuilder;

	private Clock clock = Clock.systemUTC();

	private Converter<Saml2AuthenticationRequestContext, AuthnRequest> authenticationRequestContextConverter;

	/**
	 * Creates an {@link OpenSaml4AuthenticationRequestFactory}
	 */
	public OpenSaml4AuthenticationRequestFactory() {
		this.authenticationRequestContextConverter = this::createAuthnRequest;
		XMLObjectProviderRegistry registry = ConfigurationService.get(XMLObjectProviderRegistry.class);
		this.authnRequestBuilder = (AuthnRequestBuilder) registry.getBuilderFactory()
				.getBuilder(AuthnRequest.DEFAULT_ELEMENT_NAME);
		this.issuerBuilder = (IssuerBuilder) registry.getBuilderFactory().getBuilder(Issuer.DEFAULT_ELEMENT_NAME);
		this.nameIdPolicyBuilder = (NameIDPolicyBuilder) registry.getBuilderFactory()
				.getBuilder(NameIDPolicy.DEFAULT_ELEMENT_NAME);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@Deprecated
	public String createAuthenticationRequest(Saml2AuthenticationRequest request) {
		RelyingPartyRegistration registration = RelyingPartyRegistration.withRegistrationId("noId")
				.assertionConsumerServiceBinding(Saml2MessageBinding.POST)
				.assertionConsumerServiceLocation(request.getAssertionConsumerServiceUrl())
				.entityId(request.getIssuer()).remoteIdpEntityId("noIssuer").idpWebSsoUrl("noUrl")
				.credentials((credentials) -> credentials.addAll(request.getCredentials())).build();
		Saml2AuthenticationRequestContext context = Saml2AuthenticationRequestContext.builder()
				.relyingPartyRegistration(registration).issuer(request.getIssuer())
				.assertionConsumerServiceUrl(request.getAssertionConsumerServiceUrl()).build();
		AuthnRequest authnRequest = this.authenticationRequestContextConverter.convert(context);
		return OpenSamlSigningUtils.serialize(OpenSamlSigningUtils.sign(authnRequest, registration));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Saml2PostAuthenticationRequest createPostAuthenticationRequest(Saml2AuthenticationRequestContext context) {
		AuthnRequest authnRequest = this.authenticationRequestContextConverter.convert(context);
		RelyingPartyRegistration registration = context.getRelyingPartyRegistration();
		if (registration.getAssertingPartyDetails().getWantAuthnRequestsSigned()) {
			OpenSamlSigningUtils.sign(authnRequest, registration);
		}
		String xml = OpenSamlSigningUtils.serialize(authnRequest);
		return Saml2PostAuthenticationRequest.withAuthenticationRequestContext(context)
				.samlRequest(Saml2Utils.samlEncode(xml.getBytes(StandardCharsets.UTF_8))).build();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Saml2RedirectAuthenticationRequest createRedirectAuthenticationRequest(
			Saml2AuthenticationRequestContext context) {
		AuthnRequest authnRequest = this.authenticationRequestContextConverter.convert(context);
		RelyingPartyRegistration registration = context.getRelyingPartyRegistration();
		String xml = OpenSamlSigningUtils.serialize(authnRequest);
		Saml2RedirectAuthenticationRequest.Builder result = Saml2RedirectAuthenticationRequest
				.withAuthenticationRequestContext(context);
		String deflatedAndEncoded = Saml2Utils.samlEncode(Saml2Utils.samlDeflate(xml));
		result.samlRequest(deflatedAndEncoded).relayState(context.getRelayState());
		if (registration.getAssertingPartyDetails().getWantAuthnRequestsSigned()) {
			QueryParametersPartial partial = OpenSamlSigningUtils.sign(registration)
					.param(Saml2ParameterNames.SAML_REQUEST, deflatedAndEncoded);
			if (StringUtils.hasText(context.getRelayState())) {
				partial.param(Saml2ParameterNames.RELAY_STATE, context.getRelayState());
			}
			Map<String, String> parameters = partial.parameters();
			return result.sigAlg(parameters.get(Saml2ParameterNames.SIG_ALG))
					.signature(parameters.get(Saml2ParameterNames.SIGNATURE)).build();
		}
		return result.build();
	}

	private AuthnRequest createAuthnRequest(Saml2AuthenticationRequestContext context) {
		String issuer = context.getIssuer();
		String destination = context.getDestination();
		String assertionConsumerServiceUrl = context.getAssertionConsumerServiceUrl();
		String protocolBinding = context.getRelyingPartyRegistration().getAssertionConsumerServiceBinding().getUrn();
		AuthnRequest auth = this.authnRequestBuilder.buildObject();
		if (auth.getID() == null) {
			auth.setID("ARQ" + UUID.randomUUID().toString().substring(1));
		}
		if (auth.getIssueInstant() == null) {
			auth.setIssueInstant(Instant.now(this.clock));
		}
		if (auth.isForceAuthn() == null) {
			auth.setForceAuthn(Boolean.FALSE);
		}
		if (auth.isPassive() == null) {
			auth.setIsPassive(Boolean.FALSE);
		}
		if (auth.getProtocolBinding() == null) {
			auth.setProtocolBinding(SAMLConstants.SAML2_POST_BINDING_URI);
		}
		auth.setProtocolBinding(protocolBinding);
		if (auth.getNameIDPolicy() == null) {
			setNameIdPolicy(auth, context.getRelyingPartyRegistration());
		}
		Issuer iss = this.issuerBuilder.buildObject();
		iss.setValue(issuer);
		auth.setIssuer(iss);
		auth.setDestination(destination);
		auth.setAssertionConsumerServiceURL(assertionConsumerServiceUrl);
		return auth;
	}

	private void setNameIdPolicy(AuthnRequest authnRequest, RelyingPartyRegistration registration) {
		if (!StringUtils.hasText(registration.getNameIdFormat())) {
			return;
		}
		NameIDPolicy nameIdPolicy = this.nameIdPolicyBuilder.buildObject();
		nameIdPolicy.setFormat(registration.getNameIdFormat());
		authnRequest.setNameIDPolicy(nameIdPolicy);
	}

	/**
	 * Set the strategy for building an {@link AuthnRequest} from a given context
	 * @param authenticationRequestContextConverter the conversion strategy to use
	 */
	public void setAuthenticationRequestContextConverter(
			Converter<Saml2AuthenticationRequestContext, AuthnRequest> authenticationRequestContextConverter) {
		Assert.notNull(authenticationRequestContextConverter, "authenticationRequestContextConverter cannot be null");
		this.authenticationRequestContextConverter = authenticationRequestContextConverter;
	}

	/**
	 * Use this {@link Clock} with {@link Instant#now()} for generating timestamps
	 * @param clock the {@link Clock} to use
	 */
	public void setClock(Clock clock) {
		Assert.notNull(clock, "clock cannot be null");
		this.clock = clock;
	}

}
