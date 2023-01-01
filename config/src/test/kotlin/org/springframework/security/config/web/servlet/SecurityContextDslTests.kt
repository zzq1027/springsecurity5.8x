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

package org.springframework.security.config.web.servlet

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.security.config.annotation.ObjectPostProcessor
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.test.SpringTestContext
import org.springframework.security.config.test.SpringTestContextExtension
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.userdetails.PasswordEncodedUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders
import org.springframework.security.web.FilterChainProxy
import org.springframework.security.web.context.*
import org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

@ExtendWith(SpringTestContextExtension::class)
class SecurityContextDslTests {

    @JvmField
    val spring = SpringTestContext(this)

    @Autowired
    lateinit var mvc: MockMvc

    @Test
    fun `configure when registering object post processor then invoked on security context persistence filter`() {
        spring.register(ObjectPostProcessorConfig::class.java).autowire()
        verify { ObjectPostProcessorConfig.objectPostProcessor.postProcess(any<SecurityContextPersistenceFilter>()) }
    }

    @EnableWebSecurity
    open class ObjectPostProcessorConfig : WebSecurityConfigurerAdapter() {
        override fun configure(http: HttpSecurity) {
            // @formatter:off
            http {
                securityContext { }
            }
            // @formatter:on
        }

        @Bean
        open fun objectPostProcessor(): ObjectPostProcessor<Any> = objectPostProcessor

        companion object {
            var objectPostProcessor: ObjectPostProcessor<Any> = spyk(ReflectingObjectPostProcessor())

            class ReflectingObjectPostProcessor : ObjectPostProcessor<Any> {
                override fun <O> postProcess(`object`: O): O = `object`
            }
        }
    }

    @Test
    fun `security context when invoked twice then uses original security context repository`() {
        spring.register(DuplicateDoesNotOverrideConfig::class.java).autowire()
        every { DuplicateDoesNotOverrideConfig.SECURITY_CONTEXT_REPOSITORY.loadContext(any<HttpRequestResponseHolder>()) } returns mockk<SecurityContext>(relaxed = true)
        mvc.perform(get("/"))
        verify(exactly = 1) { DuplicateDoesNotOverrideConfig.SECURITY_CONTEXT_REPOSITORY.loadContext(any<HttpRequestResponseHolder>()) }
    }


    @EnableWebSecurity
    open class DuplicateDoesNotOverrideConfig : WebSecurityConfigurerAdapter() {
        override fun configure(http: HttpSecurity) {
            // @formatter:off
            http {
                securityContext {
                    securityContextRepository = SECURITY_CONTEXT_REPOSITORY
                }
                securityContext { }
            }
            // @formatter:on
        }

        companion object {
            var SECURITY_CONTEXT_REPOSITORY = mockk<SecurityContextRepository>(relaxed = true)
        }
    }

    @Test
    fun `security context when security context repository not configured then does not throw exception`() {
        spring.register(SecurityContextRepositoryDefaultsSecurityContextRepositoryConfig::class.java).autowire()
        assertDoesNotThrow { mvc.perform(get("/")) }
    }

    @EnableWebSecurity
    open class SecurityContextRepositoryDefaultsSecurityContextRepositoryConfig : WebSecurityConfigurerAdapter(true) {
        override fun configure(http: HttpSecurity) {
            // @formatter:off
            http {
                addFilterAt<WebAsyncManagerIntegrationFilter>(WebAsyncManagerIntegrationFilter())
                anonymous { }
                securityContext { }
                authorizeRequests {
                    authorize(anyRequest, permitAll)
                }
                httpBasic { }
            }
            // @formatter:on
        }

        override fun configure(auth: AuthenticationManagerBuilder) {
            // @formatter:off
            auth
                    .inMemoryAuthentication()
                    .withUser("user").password("password").roles("USER")
            // @formatter:on
        }
    }

    @Test
    fun `security context when require explicit save is true then configure SecurityContextHolderFilter`() {
        val repository = HttpSessionSecurityContextRepository()
        val testContext = spring.register(RequireExplicitSaveConfig::class.java)
        testContext.autowire()
        val filterChainProxy = testContext.context.getBean(FilterChainProxy::class.java)
        // @formatter:off
        val filterTypes = filterChainProxy.getFilters("/").toList()

        assertThat(filterTypes)
                .anyMatch { it is SecurityContextHolderFilter }
                .noneMatch { it is SecurityContextPersistenceFilter }
        // @formatter:on
        val mvcResult = mvc.perform(SecurityMockMvcRequestBuilders.formLogin()).andReturn()
        val securityContext = repository
                .loadContext(HttpRequestResponseHolder(mvcResult.request, mvcResult.response))
        assertThat(securityContext.authentication).isNotNull
    }

    @EnableWebSecurity
    open class RequireExplicitSaveConfig : WebSecurityConfigurerAdapter() {
        override fun configure(http: HttpSecurity) {
            // @formatter:off
            http {
                formLogin { }
                securityContext {
                    requireExplicitSave = true
                }
            }
            // @formatter:on
        }

        override fun configure(auth: AuthenticationManagerBuilder) {
            // @formatter:off
            auth
                    .inMemoryAuthentication()
                    .withUser(PasswordEncodedUser.user())
            // @formatter:on
        }
    }
}
