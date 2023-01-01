/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.security.config.annotation.web;

import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.test.SpringTestContext;
import org.springframework.security.config.test.SpringTestContextExtension;
import org.springframework.security.core.userdetails.PasswordEncodedUser;
import org.springframework.security.web.context.request.async.SecurityContextCallableProcessingInterceptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * @author Rob Winch
 *
 */
@ExtendWith({ MockitoExtension.class, SpringTestContextExtension.class })
public class WebSecurityConfigurerAdapterMockitoTests {

	ConfigurableWebApplicationContext context;

	public final SpringTestContext spring = new SpringTestContext(this);

	@Autowired
	private MockMvc mockMvc;

	@Mock
	private MockedStatic<SpringFactoriesLoader> springFactoriesLoader;

	@AfterEach
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void loadConfigWhenDefaultConfigurerAsSpringFactoryhenDefaultConfigurerApplied() {
		DefaultConfigurer configurer = new DefaultConfigurer();
		this.springFactoriesLoader.when(
				() -> SpringFactoriesLoader.loadFactories(AbstractHttpConfigurer.class, getClass().getClassLoader()))
				.thenReturn(Arrays.asList(configurer));
		loadConfig(Config.class);
		assertThat(configurer.init).isTrue();
		assertThat(configurer.configure).isTrue();
	}

	@Test
	public void loadConfigWhenDefaultConfigThenWebAsyncManagerIntegrationFilterAdded() throws Exception {
		this.spring.register(WebAsyncPopulatedByDefaultConfig.class).autowire();
		WebAsyncManager webAsyncManager = mock(WebAsyncManager.class);
		this.mockMvc.perform(get("/").requestAttr(WebAsyncUtils.WEB_ASYNC_MANAGER_ATTRIBUTE, webAsyncManager));
		ArgumentCaptor<CallableProcessingInterceptor> callableProcessingInterceptorArgCaptor = ArgumentCaptor
				.forClass(CallableProcessingInterceptor.class);
		verify(webAsyncManager, atLeastOnce()).registerCallableInterceptor(any(),
				callableProcessingInterceptorArgCaptor.capture());
		CallableProcessingInterceptor callableProcessingInterceptor = callableProcessingInterceptorArgCaptor
				.getAllValues().stream()
				.filter((e) -> SecurityContextCallableProcessingInterceptor.class.isAssignableFrom(e.getClass()))
				.findFirst().orElse(null);
		assertThat(callableProcessingInterceptor).isNotNull();
	}

	private void loadConfig(Class<?>... classes) {
		AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
		context.setClassLoader(getClass().getClassLoader());
		context.register(classes);
		context.refresh();
		this.context = context;
	}

	@EnableWebSecurity
	static class Config extends WebSecurityConfigurerAdapter {

		@Override
		protected void configure(HttpSecurity http) {
		}

	}

	static class DefaultConfigurer extends AbstractHttpConfigurer<DefaultConfigurer, HttpSecurity> {

		boolean init;

		boolean configure;

		@Override
		public void init(HttpSecurity builder) {
			this.init = true;
		}

		@Override
		public void configure(HttpSecurity builder) {
			this.configure = true;
		}

	}

	@EnableWebSecurity
	static class WebAsyncPopulatedByDefaultConfig extends WebSecurityConfigurerAdapter {

		@Override
		protected void configure(AuthenticationManagerBuilder auth) throws Exception {
			// @formatter:off
			auth
					.inMemoryAuthentication()
					.withUser(PasswordEncodedUser.user());
			// @formatter:on
		}

		@Override
		protected void configure(HttpSecurity http) {
		}

	}

}
