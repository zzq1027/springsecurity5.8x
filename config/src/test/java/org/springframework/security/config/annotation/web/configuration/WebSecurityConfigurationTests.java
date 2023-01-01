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

package org.springframework.security.config.annotation.web.configuration;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.Filter;
import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.AbstractSecurityExpressionHandler;
import org.springframework.security.access.expression.SecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.EnableGlobalAuthentication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.test.SpringTestContext;
import org.springframework.security.config.test.SpringTestContextExtension;
import org.springframework.security.config.users.AuthenticationTestConfiguration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.FilterInvocation;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.RequestMatcherDelegatingWebInvocationPrivilegeEvaluator;
import org.springframework.security.web.access.WebInvocationPrivilegeEvaluator;
import org.springframework.security.web.access.expression.DefaultWebSecurityExpressionHandler;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link WebSecurityConfiguration}.
 *
 * @author Rob Winch
 * @author Joe Grandja
 * @author Evgeniy Cheban
 * @author Marcus Da Coregio
 */
@ExtendWith(SpringTestContextExtension.class)
public class WebSecurityConfigurationTests {

	public final SpringTestContext spring = new SpringTestContext(this);

	public SpringTestContext child = new SpringTestContext(this);

	@Autowired
	private MockMvc mockMvc;

	@Test
	public void loadConfigWhenWebSecurityConfigurersHaveOrderThenFilterChainsOrdered() {
		this.spring.register(SortedWebSecurityConfigurerAdaptersConfig.class).autowire();
		FilterChainProxy filterChainProxy = this.spring.getContext().getBean(FilterChainProxy.class);
		List<SecurityFilterChain> filterChains = filterChainProxy.getFilterChains();
		assertThat(filterChains).hasSize(6);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "");
		request.setServletPath("/ignore1");
		assertThat(filterChains.get(0).matches(request)).isTrue();
		assertThat(filterChains.get(0).getFilters()).isEmpty();
		request.setServletPath("/ignore2");
		assertThat(filterChains.get(1).matches(request)).isTrue();
		assertThat(filterChains.get(1).getFilters()).isEmpty();
		request.setServletPath("/role1/**");
		assertThat(filterChains.get(2).matches(request)).isTrue();
		request.setServletPath("/role2/**");
		assertThat(filterChains.get(3).matches(request)).isTrue();
		request.setServletPath("/role3/**");
		assertThat(filterChains.get(4).matches(request)).isTrue();
		request.setServletPath("/**");
		assertThat(filterChains.get(5).matches(request)).isTrue();
	}

	@Test
	public void loadConfigWhenSecurityFilterChainsHaveOrderThenFilterChainsOrdered() {
		this.spring.register(SortedSecurityFilterChainConfig.class).autowire();
		FilterChainProxy filterChainProxy = this.spring.getContext().getBean(FilterChainProxy.class);
		List<SecurityFilterChain> filterChains = filterChainProxy.getFilterChains();
		assertThat(filterChains).hasSize(4);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "");
		request.setServletPath("/role1/**");
		assertThat(filterChains.get(0).matches(request)).isTrue();
		request.setServletPath("/role2/**");
		assertThat(filterChains.get(1).matches(request)).isTrue();
		request.setServletPath("/role3/**");
		assertThat(filterChains.get(2).matches(request)).isTrue();
		request.setServletPath("/**");
		assertThat(filterChains.get(3).matches(request)).isTrue();
	}

	@Test
	public void loadConfigWhenSecurityFilterChainsHaveOrderOnBeanDefinitionsThenFilterChainsOrdered() {
		this.spring.register(OrderOnBeanDefinitionsSecurityFilterChainConfig.class).autowire();
		FilterChainProxy filterChainProxy = this.spring.getContext().getBean(FilterChainProxy.class);
		List<SecurityFilterChain> filterChains = filterChainProxy.getFilterChains();
		assertThat(filterChains).hasSize(2);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "");
		request.setServletPath("/role1/**");
		assertThat(filterChains.get(0).matches(request)).isTrue();
		request.setServletPath("/role2/**");
		assertThat(filterChains.get(1).matches(request)).isTrue();
	}

	@Test
	public void loadConfigWhenWebSecurityConfigurersHaveSameOrderThenThrowBeanCreationException() {
		assertThatExceptionOfType(BeanCreationException.class)
				.isThrownBy(() -> this.spring.register(DuplicateOrderConfig.class).autowire())
				.withMessageContaining("@Order on WebSecurityConfigurers must be unique")
				.withMessageContaining(DuplicateOrderConfig.WebConfigurer1.class.getName())
				.withMessageContaining(DuplicateOrderConfig.WebConfigurer2.class.getName());
	}

	@Test
	public void loadConfigWhenWebInvocationPrivilegeEvaluatorSetThenIsRegistered() {
		PrivilegeEvaluatorConfigurerAdapterConfig.PRIVILEGE_EVALUATOR = mock(WebInvocationPrivilegeEvaluator.class);
		this.spring.register(PrivilegeEvaluatorConfigurerAdapterConfig.class).autowire();
		assertThat(this.spring.getContext().getBean(WebInvocationPrivilegeEvaluator.class))
				.isSameAs(PrivilegeEvaluatorConfigurerAdapterConfig.PRIVILEGE_EVALUATOR);
	}

	@Test
	public void loadConfigWhenSecurityExpressionHandlerSetThenIsRegistered() {
		WebSecurityExpressionHandlerConfig.EXPRESSION_HANDLER = mock(SecurityExpressionHandler.class);
		given(WebSecurityExpressionHandlerConfig.EXPRESSION_HANDLER.getExpressionParser())
				.willReturn(mock(ExpressionParser.class));
		this.spring.register(WebSecurityExpressionHandlerConfig.class).autowire();
		assertThat(this.spring.getContext().getBean(SecurityExpressionHandler.class))
				.isSameAs(WebSecurityExpressionHandlerConfig.EXPRESSION_HANDLER);
	}

	@Test
	public void loadConfigWhenSecurityExpressionHandlerIsNullThenException() {
		assertThatExceptionOfType(BeanCreationException.class)
				.isThrownBy(() -> this.spring.register(NullWebSecurityExpressionHandlerConfig.class).autowire())
				.havingRootCause().isExactlyInstanceOf(IllegalArgumentException.class);
	}

	@Test
	public void loadConfigWhenDefaultSecurityExpressionHandlerThenDefaultIsRegistered() {
		this.spring.register(WebSecurityExpressionHandlerDefaultsConfig.class).autowire();
		assertThat(this.spring.getContext().getBean(SecurityExpressionHandler.class))
				.isInstanceOf(DefaultWebSecurityExpressionHandler.class);
	}

	@Test
	public void securityExpressionHandlerWhenRoleHierarchyBeanThenRoleHierarchyUsed() {
		this.spring.register(WebSecurityExpressionHandlerRoleHierarchyBeanConfig.class).autowire();
		TestingAuthenticationToken authentication = new TestingAuthenticationToken("user", "notused", "ROLE_ADMIN");
		FilterInvocation invocation = new FilterInvocation(new MockHttpServletRequest("GET", ""),
				new MockHttpServletResponse(), new MockFilterChain());
		AbstractSecurityExpressionHandler handler = this.spring.getContext()
				.getBean(AbstractSecurityExpressionHandler.class);
		EvaluationContext evaluationContext = handler.createEvaluationContext(authentication, invocation);
		Expression expression = handler.getExpressionParser().parseExpression("hasRole('ROLE_USER')");
		boolean granted = expression.getValue(evaluationContext, Boolean.class);
		assertThat(granted).isTrue();
	}

	@Test
	public void securityExpressionHandlerWhenPermissionEvaluatorBeanThenPermissionEvaluatorUsed() {
		this.spring.register(WebSecurityExpressionHandlerPermissionEvaluatorBeanConfig.class).autowire();
		TestingAuthenticationToken authentication = new TestingAuthenticationToken("user", "notused");
		FilterInvocation invocation = new FilterInvocation(new MockHttpServletRequest("GET", ""),
				new MockHttpServletResponse(), new MockFilterChain());
		AbstractSecurityExpressionHandler handler = this.spring.getContext()
				.getBean(AbstractSecurityExpressionHandler.class);
		EvaluationContext evaluationContext = handler.createEvaluationContext(authentication, invocation);
		Expression expression = handler.getExpressionParser().parseExpression("hasPermission(#study,'DELETE')");
		boolean granted = expression.getValue(evaluationContext, Boolean.class);
		assertThat(granted).isTrue();
	}

	@Test
	public void loadConfigWhenDefaultWebInvocationPrivilegeEvaluatorThenRequestMatcherIsRegistered() {
		this.spring.register(WebInvocationPrivilegeEvaluatorDefaultsConfig.class).autowire();
		assertThat(this.spring.getContext().getBean(WebInvocationPrivilegeEvaluator.class))
				.isInstanceOf(RequestMatcherDelegatingWebInvocationPrivilegeEvaluator.class);
	}

	@Test
	public void loadConfigWhenSecurityFilterChainBeanThenDefaultWebInvocationPrivilegeEvaluatorIsRegistered() {
		this.spring.register(AuthorizeRequestsFilterChainConfig.class).autowire();

		assertThat(this.spring.getContext().getBean(WebInvocationPrivilegeEvaluator.class))
				.isInstanceOf(RequestMatcherDelegatingWebInvocationPrivilegeEvaluator.class);
	}

	// SEC-2303
	@Test
	public void loadConfigWhenDefaultSecurityExpressionHandlerThenBeanResolverSet() throws Exception {
		this.spring.register(DefaultExpressionHandlerSetsBeanResolverConfig.class).autowire();
		this.mockMvc.perform(get("/")).andExpect(status().isOk());
		this.mockMvc.perform(post("/")).andExpect(status().isForbidden());
	}

	// SEC-2461
	@Test
	public void loadConfigWhenMultipleWebSecurityConfigurationThenContextLoads() {
		this.spring.register(ParentConfig.class).autowire();
		this.child.register(ChildConfig.class);
		this.child.getContext().setParent(this.spring.getContext());
		this.child.autowire();
		assertThat(this.spring.getContext().getBean("springSecurityFilterChain")).isNotNull();
		assertThat(this.child.getContext().getBean("springSecurityFilterChain")).isNotNull();
		assertThat(this.spring.getContext().containsBean("springSecurityFilterChain")).isTrue();
		assertThat(this.child.getContext().containsBean("springSecurityFilterChain")).isTrue();
	}

	// SEC-2773
	@Test
	public void getMethodDelegatingApplicationListenerWhenWebSecurityConfigurationThenIsStatic() {
		Method method = ClassUtils.getMethod(WebSecurityConfiguration.class, "delegatingApplicationListener", null);
		assertThat(Modifier.isStatic(method.getModifiers())).isTrue();
	}

	@Test
	public void loadConfigWhenBeanProxyingEnabledAndSubclassThenFilterChainsCreated() {
		this.spring.register(GlobalAuthenticationWebSecurityConfigurerAdaptersConfig.class, SubclassConfig.class)
				.autowire();
		FilterChainProxy filterChainProxy = this.spring.getContext().getBean(FilterChainProxy.class);
		List<SecurityFilterChain> filterChains = filterChainProxy.getFilterChains();
		assertThat(filterChains).hasSize(4);
	}

	@Test
	public void loadConfigWhenBothAdapterAndFilterChainConfiguredThenException() {
		assertThatExceptionOfType(BeanCreationException.class)
				.isThrownBy(() -> this.spring.register(AdapterAndFilterChainConfig.class).autowire())
				.withRootCauseExactlyInstanceOf(IllegalStateException.class)
				.withMessageContaining("Found WebSecurityConfigurerAdapter as well as SecurityFilterChain.");
	}

	@Test
	public void loadConfigWhenOnlyWebSecurityCustomizerThenDefaultFilterChainCreated() {
		this.spring.register(WebSecurityCustomizerConfig.class).autowire();
		FilterChainProxy filterChainProxy = this.spring.getContext().getBean(FilterChainProxy.class);
		List<SecurityFilterChain> filterChains = filterChainProxy.getFilterChains();
		assertThat(filterChains).hasSize(3);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "");
		request.setServletPath("/ignore1");
		assertThat(filterChains.get(0).matches(request)).isTrue();
		assertThat(filterChains.get(0).getFilters()).isEmpty();
		request.setServletPath("/ignore2");
		assertThat(filterChains.get(1).matches(request)).isTrue();
		assertThat(filterChains.get(1).getFilters()).isEmpty();
		request.setServletPath("/test/**");
		assertThat(filterChains.get(2).matches(request)).isTrue();
	}

	@Test
	public void loadConfigWhenWebSecurityCustomizerAndFilterChainThenFilterChainsOrdered() {
		this.spring.register(CustomizerAndFilterChainConfig.class).autowire();
		FilterChainProxy filterChainProxy = this.spring.getContext().getBean(FilterChainProxy.class);
		List<SecurityFilterChain> filterChains = filterChainProxy.getFilterChains();
		assertThat(filterChains).hasSize(3);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "");
		request.setServletPath("/ignore1");
		assertThat(filterChains.get(0).matches(request)).isTrue();
		assertThat(filterChains.get(0).getFilters()).isEmpty();
		request.setServletPath("/ignore2");
		assertThat(filterChains.get(1).matches(request)).isTrue();
		assertThat(filterChains.get(1).getFilters()).isEmpty();
		request.setServletPath("/role1/**");
		assertThat(filterChains.get(2).matches(request)).isTrue();
		request.setServletPath("/test/**");
		assertThat(filterChains.get(2).matches(request)).isFalse();
	}

	@Test
	public void loadConfigWhenWebSecurityCustomizerAndWebSecurityConfigurerAdapterThenFilterChainsOrdered() {
		this.spring.register(CustomizerAndAdapterConfig.class).autowire();
		FilterChainProxy filterChainProxy = this.spring.getContext().getBean(FilterChainProxy.class);
		List<SecurityFilterChain> filterChains = filterChainProxy.getFilterChains();
		assertThat(filterChains).hasSize(3);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "");
		request.setServletPath("/ignore1");
		assertThat(filterChains.get(0).matches(request)).isTrue();
		assertThat(filterChains.get(0).getFilters()).isEmpty();
		request.setServletPath("/ignore2");
		assertThat(filterChains.get(1).matches(request)).isTrue();
		assertThat(filterChains.get(1).getFilters()).isEmpty();
		request.setServletPath("/role1/**");
		assertThat(filterChains.get(2).matches(request)).isTrue();
		request.setServletPath("/test/**");
		assertThat(filterChains.get(2).matches(request)).isFalse();
	}

	@Test
	public void loadConfigWhenCustomizerAndAdapterConfigureWebSecurityThenBothConfigurationsApplied() {
		this.spring.register(CustomizerAndAdapterIgnoringConfig.class).autowire();
		FilterChainProxy filterChainProxy = this.spring.getContext().getBean(FilterChainProxy.class);
		List<SecurityFilterChain> filterChains = filterChainProxy.getFilterChains();
		assertThat(filterChains).hasSize(3);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "");
		request.setServletPath("/ignore1");
		assertThat(filterChains.get(0).matches(request)).isTrue();
		assertThat(filterChains.get(0).getFilters()).isEmpty();
		request.setServletPath("/ignore2");
		assertThat(filterChains.get(1).matches(request)).isTrue();
		assertThat(filterChains.get(1).getFilters()).isEmpty();
	}

	@Test
	public void loadConfigWhenCustomizersHaveOrderThenCustomizersOrdered() {
		this.spring.register(OrderedCustomizerConfig.class).autowire();
		FilterChainProxy filterChainProxy = this.spring.getContext().getBean(FilterChainProxy.class);
		List<SecurityFilterChain> filterChains = filterChainProxy.getFilterChains();
		assertThat(filterChains).hasSize(3);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "");
		request.setServletPath("/ignore1");
		assertThat(filterChains.get(0).matches(request)).isTrue();
		assertThat(filterChains.get(0).getFilters()).isEmpty();
		request.setServletPath("/ignore2");
		assertThat(filterChains.get(1).matches(request)).isTrue();
		assertThat(filterChains.get(1).getFilters()).isEmpty();
	}

	@Test
	public void loadConfigWhenMultipleAuthenticationManagersAndWebSecurityConfigurerAdapterThenConfigurationApplied() {
		this.spring.register(MultipleAuthenticationManagersConfig.class).autowire();
		FilterChainProxy filterChainProxy = this.spring.getContext().getBean(FilterChainProxy.class);
		List<SecurityFilterChain> filterChains = filterChainProxy.getFilterChains();
		assertThat(filterChains).hasSize(2);
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "");
		request.setServletPath("/role1");
		assertThat(filterChains.get(0).matches(request)).isTrue();
		request.setServletPath("/role2");
		assertThat(filterChains.get(1).matches(request)).isTrue();
	}

	@Test
	public void loadConfigWhenTwoSecurityFilterChainsThenRequestMatcherDelegatingWebInvocationPrivilegeEvaluator() {
		this.spring.register(TwoSecurityFilterChainConfig.class).autowire();
		assertThat(this.spring.getContext().getBean(WebInvocationPrivilegeEvaluator.class))
				.isInstanceOf(RequestMatcherDelegatingWebInvocationPrivilegeEvaluator.class);
	}

	@Test
	public void loadConfigWhenTwoSecurityFilterChainDebugThenRequestMatcherDelegatingWebInvocationPrivilegeEvaluator() {
		this.spring.register(TwoSecurityFilterChainConfig.class).autowire();
		assertThat(this.spring.getContext().getBean(WebInvocationPrivilegeEvaluator.class))
				.isInstanceOf(RequestMatcherDelegatingWebInvocationPrivilegeEvaluator.class);
	}

	// gh-10554
	@Test
	public void loadConfigWhenMultipleSecurityFilterChainsThenWebInvocationPrivilegeEvaluatorApplySecurity() {
		this.spring.register(MultipleSecurityFilterChainConfig.class).autowire();
		WebInvocationPrivilegeEvaluator privilegeEvaluator = this.spring.getContext()
				.getBean(WebInvocationPrivilegeEvaluator.class);
		assertUserPermissions(privilegeEvaluator);
		assertAdminPermissions(privilegeEvaluator);
		assertAnotherUserPermission(privilegeEvaluator);
	}

	// gh-10554
	@Test
	public void loadConfigWhenMultipleSecurityFilterChainAndIgnoringThenWebInvocationPrivilegeEvaluatorAcceptsNullAuthenticationOnIgnored() {
		this.spring.register(MultipleSecurityFilterChainIgnoringConfig.class).autowire();
		WebInvocationPrivilegeEvaluator privilegeEvaluator = this.spring.getContext()
				.getBean(WebInvocationPrivilegeEvaluator.class);
		assertUserPermissions(privilegeEvaluator);
		assertAdminPermissions(privilegeEvaluator);
		assertAnotherUserPermission(privilegeEvaluator);
		// null authentication
		assertThat(privilegeEvaluator.isAllowed("/user", null)).isFalse();
		assertThat(privilegeEvaluator.isAllowed("/admin", null)).isFalse();
		assertThat(privilegeEvaluator.isAllowed("/another", null)).isFalse();
		assertThat(privilegeEvaluator.isAllowed("/ignoring1", null)).isTrue();
		assertThat(privilegeEvaluator.isAllowed("/ignoring1/child", null)).isTrue();
	}

	private void assertAnotherUserPermission(WebInvocationPrivilegeEvaluator privilegeEvaluator) {
		Authentication anotherUser = new TestingAuthenticationToken("anotherUser", "password", "ROLE_ANOTHER");
		assertThat(privilegeEvaluator.isAllowed("/user", anotherUser)).isFalse();
		assertThat(privilegeEvaluator.isAllowed("/admin", anotherUser)).isFalse();
		assertThat(privilegeEvaluator.isAllowed("/another", anotherUser)).isTrue();
	}

	private void assertAdminPermissions(WebInvocationPrivilegeEvaluator privilegeEvaluator) {
		Authentication admin = new TestingAuthenticationToken("admin", "password", "ROLE_ADMIN");
		assertThat(privilegeEvaluator.isAllowed("/user", admin)).isFalse();
		assertThat(privilegeEvaluator.isAllowed("/admin", admin)).isTrue();
		assertThat(privilegeEvaluator.isAllowed("/another", admin)).isTrue();
	}

	private void assertUserPermissions(WebInvocationPrivilegeEvaluator privilegeEvaluator) {
		Authentication user = new TestingAuthenticationToken("user", "password", "ROLE_USER");
		assertThat(privilegeEvaluator.isAllowed("/user", user)).isTrue();
		assertThat(privilegeEvaluator.isAllowed("/admin", user)).isFalse();
		assertThat(privilegeEvaluator.isAllowed("/another", user)).isTrue();
	}

	@EnableWebSecurity
	@Import(AuthenticationTestConfiguration.class)
	static class SortedWebSecurityConfigurerAdaptersConfig {

		@Configuration
		@Order(1)
		static class WebConfigurer1 extends WebSecurityConfigurerAdapter {

			@Override
			public void configure(WebSecurity web) {
				web.ignoring().antMatchers("/ignore1", "/ignore2");
			}

			@Override
			protected void configure(HttpSecurity http) throws Exception {
				// @formatter:off
				http
					.antMatcher("/role1/**")
					.authorizeRequests()
						.anyRequest().hasRole("1");
				// @formatter:on
			}

		}

		@Configuration
		@Order(2)
		static class WebConfigurer2 extends WebSecurityConfigurerAdapter {

			@Override
			protected void configure(HttpSecurity http) throws Exception {
				// @formatter:off
				http
					.antMatcher("/role2/**")
					.authorizeRequests()
						.anyRequest().hasRole("2");
				// @formatter:on
			}

		}

		@Configuration
		@Order(3)
		static class WebConfigurer3 extends WebSecurityConfigurerAdapter {

			@Override
			protected void configure(HttpSecurity http) throws Exception {
				// @formatter:off
				http
					.antMatcher("/role3/**")
					.authorizeRequests()
						.anyRequest().hasRole("3");
				// @formatter:on
			}

		}

		@Configuration
		static class WebConfigurer4 extends WebSecurityConfigurerAdapter {

			@Override
			protected void configure(HttpSecurity http) throws Exception {
				// @formatter:off
				http
					.authorizeRequests()
						.anyRequest().hasRole("4");
				// @formatter:on
			}

		}

	}

	@EnableWebSecurity
	@Import(AuthenticationTestConfiguration.class)
	static class SortedSecurityFilterChainConfig {

		@Order(1)
		@Bean
		SecurityFilterChain filterChain1(HttpSecurity http) throws Exception {
			// @formatter:off
			return http
					.antMatcher("/role1/**")
					.authorizeRequests((authorize) -> authorize
							.anyRequest().hasRole("1")
					)
					.build();
			// @formatter:on
		}

		@Order(2)
		@Bean
		SecurityFilterChain filterChain2(HttpSecurity http) throws Exception {
			// @formatter:off
			return http
					.antMatcher("/role2/**")
					.authorizeRequests((authorize) -> authorize
							.anyRequest().hasRole("2")
					)
					.build();
			// @formatter:on
		}

		@Order(3)
		@Bean
		SecurityFilterChain filterChain3(HttpSecurity http) throws Exception {
			// @formatter:off
			return http
					.antMatcher("/role3/**")
					.authorizeRequests((authorize) -> authorize
							.anyRequest().hasRole("3")
					)
					.build();
			// @formatter:on
		}

		@Bean
		SecurityFilterChain filterChain4(HttpSecurity http) throws Exception {
			// @formatter:off
			return http
					.authorizeRequests((authorize) -> authorize
						.anyRequest().hasRole("4")
					)
					.build();
			// @formatter:on
		}

	}

	@EnableWebSecurity
	@Import(AuthenticationTestConfiguration.class)
	static class OrderOnBeanDefinitionsSecurityFilterChainConfig {

		@Bean
		@Order(1)
		SecurityFilterChain securityFilterChain1(HttpSecurity http) throws Exception {
			// @formatter:off
			return http
					.antMatcher("/role1/**")
					.authorizeRequests((authorize) -> authorize
							.anyRequest().hasRole("1")
					)
					.build();
			// @formatter:on
		}

		@Bean
		TestSecurityFilterChain securityFilterChain2(HttpSecurity http) throws Exception {
			return new TestSecurityFilterChain();
		}

		@Order(2)
		static class TestSecurityFilterChain implements SecurityFilterChain {

			@Override
			public boolean matches(HttpServletRequest request) {
				return true;
			}

			@Override
			public List<Filter> getFilters() {
				return new ArrayList<>();
			}

		}

	}

	@EnableWebSecurity
	@Import(AuthenticationTestConfiguration.class)
	static class DuplicateOrderConfig {

		@Configuration
		static class WebConfigurer1 extends WebSecurityConfigurerAdapter {

			@Override
			protected void configure(HttpSecurity http) throws Exception {
				// @formatter:off
				http
					.antMatcher("/role1/**")
						.authorizeRequests()
							.anyRequest().hasRole("1");
				// @formatter:on
			}

		}

		@Configuration
		static class WebConfigurer2 extends WebSecurityConfigurerAdapter {

			@Override
			protected void configure(HttpSecurity http) throws Exception {
				// @formatter:off
				http
					.antMatcher("/role2/**")
						.authorizeRequests()
							.anyRequest().hasRole("2");
				// @formatter:on
			}

		}

	}

	@EnableWebSecurity
	static class PrivilegeEvaluatorConfigurerAdapterConfig extends WebSecurityConfigurerAdapter {

		static WebInvocationPrivilegeEvaluator PRIVILEGE_EVALUATOR;

		@Override
		public void configure(WebSecurity web) {
			web.privilegeEvaluator(PRIVILEGE_EVALUATOR);
		}

	}

	@EnableWebSecurity
	static class WebSecurityExpressionHandlerConfig extends WebSecurityConfigurerAdapter {

		static SecurityExpressionHandler EXPRESSION_HANDLER;

		@Override
		public void configure(WebSecurity web) {
			web.expressionHandler(EXPRESSION_HANDLER);
		}

		@Override
		protected void configure(HttpSecurity http) throws Exception {
			// @formatter:off
			http
				.authorizeRequests()
					.anyRequest().authenticated()
					.expressionHandler(EXPRESSION_HANDLER);
			// @formatter:on
		}

	}

	@EnableWebSecurity
	static class NullWebSecurityExpressionHandlerConfig extends WebSecurityConfigurerAdapter {

		@Override
		public void configure(WebSecurity web) {
			web.expressionHandler(null);
		}

	}

	@EnableWebSecurity
	static class WebSecurityExpressionHandlerDefaultsConfig extends WebSecurityConfigurerAdapter {

		@Override
		protected void configure(HttpSecurity http) throws Exception {
			// @formatter:off
			http
				.authorizeRequests()
					.anyRequest().authenticated();
			// @formatter:on
		}

	}

	@EnableWebSecurity
	static class WebSecurityExpressionHandlerRoleHierarchyBeanConfig extends WebSecurityConfigurerAdapter {

		@Bean
		RoleHierarchy roleHierarchy() {
			RoleHierarchyImpl roleHierarchy = new RoleHierarchyImpl();
			roleHierarchy.setHierarchy("ROLE_ADMIN > ROLE_USER");
			return roleHierarchy;
		}

	}

	@EnableWebSecurity
	static class WebSecurityExpressionHandlerPermissionEvaluatorBeanConfig extends WebSecurityConfigurerAdapter {

		static final PermissionEvaluator PERMIT_ALL_PERMISSION_EVALUATOR = new PermissionEvaluator() {
			@Override
			public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
				return true;
			}

			@Override
			public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType,
					Object permission) {
				return true;
			}
		};

		@Bean
		PermissionEvaluator permissionEvaluator() {
			return PERMIT_ALL_PERMISSION_EVALUATOR;
		}

	}

	@EnableWebSecurity
	static class WebInvocationPrivilegeEvaluatorDefaultsConfig extends WebSecurityConfigurerAdapter {

		@Override
		protected void configure(HttpSecurity http) throws Exception {
			// @formatter:off
			http
				.authorizeRequests()
					.anyRequest().authenticated();
			// @formatter:on
		}

	}

	@EnableWebSecurity
	static class AuthorizeRequestsFilterChainConfig {

		@Bean
		public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
			// @formatter:off
			return http
					.authorizeRequests((authorize) -> authorize
						.anyRequest().authenticated()
					)
					.build();
			// @formatter:on
		}

	}

	@EnableWebSecurity
	static class DefaultExpressionHandlerSetsBeanResolverConfig extends WebSecurityConfigurerAdapter {

		@Override
		protected void configure(HttpSecurity http) throws Exception {
			// @formatter:off
			http
				.authorizeRequests()
					.anyRequest().access("request.method == 'GET' ? @b.grant() : @b.deny()");
			// @formatter:on
		}

		@Bean
		public MyBean b() {
			return new MyBean();
		}

		@RestController
		class HomeController {

			@GetMapping("/")
			String home() {
				return "home";
			}

		}

		static class MyBean {

			public boolean deny() {
				return false;
			}

			public boolean grant() {
				return true;
			}

		}

	}

	@EnableWebSecurity
	static class ParentConfig extends WebSecurityConfigurerAdapter {

		@Autowired
		void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
			auth.inMemoryAuthentication();
		}

	}

	@EnableWebSecurity
	static class ChildConfig extends WebSecurityConfigurerAdapter {

	}

	@Configuration
	static class SubclassConfig extends WebSecurityConfiguration {

	}

	@Import(AuthenticationTestConfiguration.class)
	@EnableGlobalAuthentication
	static class GlobalAuthenticationWebSecurityConfigurerAdaptersConfig {

		@Configuration
		@Order(1)
		static class WebConfigurer1 extends WebSecurityConfigurerAdapter {

			@Override
			public void configure(WebSecurity web) {
				web.ignoring().antMatchers("/ignore1", "/ignore2");
			}

			@Override
			protected void configure(HttpSecurity http) throws Exception {
				// @formatter:off
				http
						.antMatcher("/anonymous/**")
						.authorizeRequests()
						.anyRequest().anonymous();
				// @formatter:on
			}

		}

		@Configuration
		static class WebConfigurer2 extends WebSecurityConfigurerAdapter {

			@Override
			protected void configure(HttpSecurity http) throws Exception {
				// @formatter:off
				http
						.authorizeRequests()
						.anyRequest().authenticated();
				// @formatter:on
			}

		}

	}

	@EnableWebSecurity
	@Import(AuthenticationTestConfiguration.class)
	static class AdapterAndFilterChainConfig {

		@Order(2)
		@Bean
		SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
			// @formatter:off
			return http
					.antMatcher("/filter/**")
					.authorizeRequests((authorize) -> authorize
							.anyRequest().authenticated()
					)
					.build();
			// @formatter:on
		}

		@Order(1)
		@Configuration
		static class WebConfigurer extends WebSecurityConfigurerAdapter {

			@Override
			protected void configure(HttpSecurity http) throws Exception {
				// @formatter:off
				http
					.antMatcher("/config/**")
					.authorizeRequests((authorize) -> authorize
							.anyRequest().permitAll()
					);
				// @formatter:on
			}

		}

	}

	@EnableWebSecurity
	@Import(AuthenticationTestConfiguration.class)
	static class WebSecurityCustomizerConfig {

		@Bean
		public WebSecurityCustomizer webSecurityCustomizer() {
			return (web) -> web.ignoring().antMatchers("/ignore1", "/ignore2");
		}

	}

	@EnableWebSecurity
	@Import(AuthenticationTestConfiguration.class)
	static class CustomizerAndFilterChainConfig {

		@Bean
		public WebSecurityCustomizer webSecurityCustomizer() {
			return (web) -> web.ignoring().antMatchers("/ignore1", "/ignore2");
		}

		@Bean
		SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
			// @formatter:off
			return http
					.antMatcher("/role1/**")
					.authorizeRequests((authorize) -> authorize
							.anyRequest().hasRole("1")
					)
					.build();
			// @formatter:on
		}

	}

	@EnableWebSecurity
	@Import(AuthenticationTestConfiguration.class)
	static class CustomizerAndAdapterConfig {

		@Bean
		public WebSecurityCustomizer webSecurityCustomizer() {
			return (web) -> web.ignoring().antMatchers("/ignore1", "/ignore2");
		}

		@Configuration
		static class SecurityConfig extends WebSecurityConfigurerAdapter {

			@Override
			protected void configure(HttpSecurity http) throws Exception {
				// @formatter:off
				http
						.antMatcher("/role1/**")
						.authorizeRequests((authorize) -> authorize
								.anyRequest().hasRole("1")
						);
				// @formatter:on
			}

		}

	}

	@EnableWebSecurity
	@Import(AuthenticationTestConfiguration.class)
	static class CustomizerAndAdapterIgnoringConfig {

		@Bean
		public WebSecurityCustomizer webSecurityCustomizer() {
			return (web) -> web.ignoring().antMatchers("/ignore1");
		}

		@Configuration
		static class SecurityConfig extends WebSecurityConfigurerAdapter {

			@Override
			public void configure(WebSecurity web) throws Exception {
				web.ignoring().antMatchers("/ignore2");
			}

		}

	}

	@EnableWebSecurity
	@Import(AuthenticationTestConfiguration.class)
	static class OrderedCustomizerConfig {

		@Order(1)
		@Bean
		public WebSecurityCustomizer webSecurityCustomizer1() {
			return (web) -> web.ignoring().antMatchers("/ignore1");
		}

		@Order(2)
		@Bean
		public WebSecurityCustomizer webSecurityCustomizer2() {
			return (web) -> web.ignoring().antMatchers("/ignore2");
		}

	}

	@EnableWebSecurity
	static class MultipleAuthenticationManagersConfig {

		@Bean("authManager1")
		static AuthenticationManager authenticationManager1() {
			return new ProviderManager(new AuthenticationProvider() {
				@Override
				public Authentication authenticate(Authentication authentication) throws AuthenticationException {
					return UsernamePasswordAuthenticationToken.unauthenticated("user", "credentials");
				}

				@Override
				public boolean supports(Class<?> authentication) {
					return false;
				}
			});
		}

		@Bean("authManager2")
		static AuthenticationManager authenticationManager2() {
			return new ProviderManager(new AuthenticationProvider() {
				@Override
				public Authentication authenticate(Authentication authentication) throws AuthenticationException {
					return UsernamePasswordAuthenticationToken.unauthenticated("subuser", "credentials");
				}

				@Override
				public boolean supports(Class<?> authentication) {
					return false;
				}
			});
		}

		@Configuration
		@Order(1)
		public static class SecurityConfig1 extends WebSecurityConfigurerAdapter {

			@Override
			protected AuthenticationManager authenticationManager() {
				return authenticationManager1();
			}

			@Override
			protected void configure(HttpSecurity http) throws Exception {
				// @formatter:off
				http
						.antMatcher("/role1/**")
						.authorizeRequests((authorize) -> authorize
								.anyRequest().hasRole("1")
						);
				// @formatter:on
			}

		}

		@Configuration
		@Order(2)
		public static class SecurityConfig2 extends WebSecurityConfigurerAdapter {

			@Override
			protected AuthenticationManager authenticationManager() {
				return authenticationManager2();
			}

		}

	}

	@EnableWebSecurity
	static class TwoSecurityFilterChainConfig {

		@Bean
		@Order(Ordered.HIGHEST_PRECEDENCE)
		public SecurityFilterChain path1(HttpSecurity http) throws Exception {
			// @formatter:off
			http
				.requestMatchers((requests) -> requests.antMatchers("/path1/**"))
				.authorizeRequests((requests) -> requests.anyRequest().authenticated());
			// @formatter:on
			return http.build();
		}

		@Bean
		@Order(Ordered.LOWEST_PRECEDENCE)
		public SecurityFilterChain permitAll(HttpSecurity http) throws Exception {
			http.authorizeRequests((requests) -> requests.anyRequest().permitAll());
			return http.build();
		}

	}

	@EnableWebSecurity(debug = true)
	static class TwoSecurityFilterChainDebugConfig {

		@Bean
		@Order(Ordered.HIGHEST_PRECEDENCE)
		public SecurityFilterChain path1(HttpSecurity http) throws Exception {
			// @formatter:off
			http
					.requestMatchers((requests) -> requests.antMatchers("/path1/**"))
					.authorizeRequests((requests) -> requests.anyRequest().authenticated());
			// @formatter:on
			return http.build();
		}

		@Bean
		@Order(Ordered.LOWEST_PRECEDENCE)
		public SecurityFilterChain permitAll(HttpSecurity http) throws Exception {
			http.authorizeRequests((requests) -> requests.anyRequest().permitAll());
			return http.build();
		}

	}

	@EnableWebSecurity
	@Import(AuthenticationTestConfiguration.class)
	static class MultipleSecurityFilterChainConfig {

		@Bean
		@Order(Ordered.HIGHEST_PRECEDENCE)
		public SecurityFilterChain notAuthorized(HttpSecurity http) throws Exception {
			// @formatter:off
			http
				.requestMatchers((requests) -> requests.antMatchers("/user"))
				.authorizeRequests((requests) -> requests.anyRequest().hasRole("USER"));
			// @formatter:on
			return http.build();
		}

		@Bean
		@Order(Ordered.HIGHEST_PRECEDENCE + 1)
		public SecurityFilterChain path1(HttpSecurity http) throws Exception {
			// @formatter:off
			http
				.requestMatchers((requests) -> requests.antMatchers("/admin"))
				.authorizeRequests((requests) -> requests.anyRequest().hasRole("ADMIN"));
			// @formatter:on
			return http.build();
		}

		@Bean
		@Order(Ordered.LOWEST_PRECEDENCE)
		public SecurityFilterChain permitAll(HttpSecurity http) throws Exception {
			http.authorizeRequests((requests) -> requests.anyRequest().permitAll());
			return http.build();
		}

	}

	@EnableWebSecurity
	@Import(AuthenticationTestConfiguration.class)
	static class MultipleSecurityFilterChainIgnoringConfig {

		@Bean
		public WebSecurityCustomizer webSecurityCustomizer() {
			return (web) -> web.ignoring().antMatchers("/ignoring1/**");
		}

		@Bean
		@Order(Ordered.HIGHEST_PRECEDENCE)
		public SecurityFilterChain notAuthorized(HttpSecurity http) throws Exception {
			// @formatter:off
			http
					.requestMatchers((requests) -> requests.antMatchers("/user"))
					.authorizeRequests((requests) -> requests.anyRequest().hasRole("USER"));
			// @formatter:on
			return http.build();
		}

		@Bean
		@Order(Ordered.HIGHEST_PRECEDENCE + 1)
		public SecurityFilterChain admin(HttpSecurity http) throws Exception {
			// @formatter:off
			http
					.requestMatchers((requests) -> requests.antMatchers("/admin"))
					.authorizeRequests((requests) -> requests.anyRequest().hasRole("ADMIN"));
			// @formatter:on
			return http.build();
		}

		@Bean
		@Order(Ordered.LOWEST_PRECEDENCE)
		public SecurityFilterChain permitAll(HttpSecurity http) throws Exception {
			http.authorizeRequests((requests) -> requests.anyRequest().permitAll());
			return http.build();
		}

	}

}
