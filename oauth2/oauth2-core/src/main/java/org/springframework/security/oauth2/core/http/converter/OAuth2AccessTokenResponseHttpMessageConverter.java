/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.security.oauth2.core.http.converter;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.oauth2.core.endpoint.DefaultMapOAuth2AccessTokenResponseConverter;
import org.springframework.security.oauth2.core.endpoint.DefaultOAuth2AccessTokenResponseMapConverter;
import org.springframework.security.oauth2.core.endpoint.MapOAuth2AccessTokenResponseConverter;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponseMapConverter;
import org.springframework.util.Assert;

/**
 * A {@link HttpMessageConverter} for an {@link OAuth2AccessTokenResponse OAuth 2.0 Access
 * Token Response}.
 *
 * @author Joe Grandja
 * @since 5.1
 * @see AbstractHttpMessageConverter
 * @see OAuth2AccessTokenResponse
 */
public class OAuth2AccessTokenResponseHttpMessageConverter
		extends AbstractHttpMessageConverter<OAuth2AccessTokenResponse> {

	private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

	private static final ParameterizedTypeReference<Map<String, Object>> STRING_OBJECT_MAP = new ParameterizedTypeReference<Map<String, Object>>() {
	};

	private GenericHttpMessageConverter<Object> jsonMessageConverter = HttpMessageConverters.getJsonMessageConverter();

	/**
	 * @deprecated This field should no longer be used
	 */
	@Deprecated
	protected Converter<Map<String, String>, OAuth2AccessTokenResponse> tokenResponseConverter = new MapOAuth2AccessTokenResponseConverter();

	private Converter<Map<String, Object>, OAuth2AccessTokenResponse> accessTokenResponseConverter = new DefaultMapOAuth2AccessTokenResponseConverter();

	/**
	 * @deprecated This field should no longer be used
	 */
	@Deprecated
	protected Converter<OAuth2AccessTokenResponse, Map<String, String>> tokenResponseParametersConverter = new OAuth2AccessTokenResponseMapConverter();

	private Converter<OAuth2AccessTokenResponse, Map<String, Object>> accessTokenResponseParametersConverter = new DefaultOAuth2AccessTokenResponseMapConverter();

	public OAuth2AccessTokenResponseHttpMessageConverter() {
		super(DEFAULT_CHARSET, MediaType.APPLICATION_JSON, new MediaType("application", "*+json"));
	}

	@Override
	protected boolean supports(Class<?> clazz) {
		return OAuth2AccessTokenResponse.class.isAssignableFrom(clazz);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected OAuth2AccessTokenResponse readInternal(Class<? extends OAuth2AccessTokenResponse> clazz,
			HttpInputMessage inputMessage) throws HttpMessageNotReadableException {
		try {
			Map<String, Object> tokenResponseParameters = (Map<String, Object>) this.jsonMessageConverter
					.read(STRING_OBJECT_MAP.getType(), null, inputMessage);
			// Only use deprecated converter if it has been set directly
			if (this.tokenResponseConverter.getClass() != MapOAuth2AccessTokenResponseConverter.class) {
				// gh-6463: Parse parameter values as Object in order to handle potential
				// JSON Object and then convert values to String
				Map<String, String> stringTokenResponseParameters = new HashMap<>();
				tokenResponseParameters
						.forEach((key, value) -> stringTokenResponseParameters.put(key, String.valueOf(value)));
				return this.tokenResponseConverter.convert(stringTokenResponseParameters);
			}
			return this.accessTokenResponseConverter.convert(tokenResponseParameters);
		}
		catch (Exception ex) {
			throw new HttpMessageNotReadableException(
					"An error occurred reading the OAuth 2.0 Access Token Response: " + ex.getMessage(), ex,
					inputMessage);
		}
	}

	@Override
	protected void writeInternal(OAuth2AccessTokenResponse tokenResponse, HttpOutputMessage outputMessage)
			throws HttpMessageNotWritableException {
		try {
			Map<String, Object> tokenResponseParameters;
			// Only use deprecated converter if it has been set directly
			if (this.tokenResponseParametersConverter.getClass() != OAuth2AccessTokenResponseMapConverter.class) {
				tokenResponseParameters = new LinkedHashMap<>(
						this.tokenResponseParametersConverter.convert(tokenResponse));
			}
			else {
				tokenResponseParameters = this.accessTokenResponseParametersConverter.convert(tokenResponse);
			}
			this.jsonMessageConverter.write(tokenResponseParameters, STRING_OBJECT_MAP.getType(),
					MediaType.APPLICATION_JSON, outputMessage);
		}
		catch (Exception ex) {
			throw new HttpMessageNotWritableException(
					"An error occurred writing the OAuth 2.0 Access Token Response: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Sets the {@link Converter} used for converting the OAuth 2.0 Access Token Response
	 * parameters to an {@link OAuth2AccessTokenResponse}.
	 * @param tokenResponseConverter the {@link Converter} used for converting to an
	 * {@link OAuth2AccessTokenResponse}
	 * @deprecated Use {@link #setAccessTokenResponseConverter(Converter)} instead
	 */
	@Deprecated
	public final void setTokenResponseConverter(
			Converter<Map<String, String>, OAuth2AccessTokenResponse> tokenResponseConverter) {
		Assert.notNull(tokenResponseConverter, "tokenResponseConverter cannot be null");
		this.tokenResponseConverter = tokenResponseConverter;
	}

	/**
	 * Sets the {@link Converter} used for converting the OAuth 2.0 Access Token Response
	 * parameters to an {@link OAuth2AccessTokenResponse}.
	 * @param accessTokenResponseConverter the {@link Converter} used for converting to an
	 * {@link OAuth2AccessTokenResponse}
	 * @since 5.6
	 */
	public final void setAccessTokenResponseConverter(
			Converter<Map<String, Object>, OAuth2AccessTokenResponse> accessTokenResponseConverter) {
		Assert.notNull(accessTokenResponseConverter, "accessTokenResponseConverter cannot be null");
		this.accessTokenResponseConverter = accessTokenResponseConverter;
	}

	/**
	 * Sets the {@link Converter} used for converting the
	 * {@link OAuth2AccessTokenResponse} to a {@code Map} representation of the OAuth 2.0
	 * Access Token Response parameters.
	 * @param tokenResponseParametersConverter the {@link Converter} used for converting
	 * to a {@code Map} representation of the Access Token Response parameters
	 * @deprecated Use {@link #setAccessTokenResponseParametersConverter(Converter)}
	 * instead
	 */
	@Deprecated
	public final void setTokenResponseParametersConverter(
			Converter<OAuth2AccessTokenResponse, Map<String, String>> tokenResponseParametersConverter) {
		Assert.notNull(tokenResponseParametersConverter, "tokenResponseParametersConverter cannot be null");
		this.tokenResponseParametersConverter = tokenResponseParametersConverter;
	}

	/**
	 * Sets the {@link Converter} used for converting the
	 * {@link OAuth2AccessTokenResponse} to a {@code Map} representation of the OAuth 2.0
	 * Access Token Response parameters.
	 * @param accessTokenResponseParametersConverter the {@link Converter} used for
	 * converting to a {@code Map} representation of the Access Token Response parameters
	 * @since 5.6
	 */
	public final void setAccessTokenResponseParametersConverter(
			Converter<OAuth2AccessTokenResponse, Map<String, Object>> accessTokenResponseParametersConverter) {
		Assert.notNull(accessTokenResponseParametersConverter, "accessTokenResponseParametersConverter cannot be null");
		this.accessTokenResponseParametersConverter = accessTokenResponseParametersConverter;
	}

}
