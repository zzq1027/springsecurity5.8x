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

package org.springframework.security.config.web.servlet

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.BeanCreationException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.test.SpringTestContext
import org.springframework.security.config.test.SpringTestContextExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.servlet.config.annotation.EnableWebMvc

/**
 * Tests for [CorsDsl]
 *
 * @author Eleftheria Stein
 */
@ExtendWith(SpringTestContextExtension::class)
class CorsDslTests {
    @JvmField
    val spring = SpringTestContext(this)

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `CORS when no MVC then exception`() {
        assertThatThrownBy { this.spring.register(DefaultCorsConfig::class.java).autowire() }
                .isInstanceOf(BeanCreationException::class.java)
                .hasMessageContaining("Please ensure Spring Security & Spring MVC are configured in a shared ApplicationContext")

    }

    @EnableWebSecurity
    open class DefaultCorsConfig : WebSecurityConfigurerAdapter() {
        override fun configure(http: HttpSecurity) {
            http {
                cors { }
            }
        }
    }

    @Test
    fun `CORS when CORS configuration source bean then responds with CORS header`() {
        this.spring.register(CorsCrossOriginBeanConfig::class.java).autowire()

        this.mockMvc.get("/")
        {
            header(HttpHeaders.ORIGIN, "https://example.com")
        }.andExpect {
            header { exists("Access-Control-Allow-Origin") }
        }
    }

    @EnableWebMvc
    @EnableWebSecurity
    open class CorsCrossOriginBeanConfig : WebSecurityConfigurerAdapter() {
        override fun configure(http: HttpSecurity) {
            http {
                cors { }
            }
        }

        @Bean
        open fun corsConfigurationSource(): CorsConfigurationSource {
            val source = UrlBasedCorsConfigurationSource()
            val corsConfiguration = CorsConfiguration()
            corsConfiguration.allowedOrigins = listOf("*")
            corsConfiguration.allowedMethods = listOf(
                    RequestMethod.GET.name,
                    RequestMethod.POST.name)
            source.registerCorsConfiguration("/**", corsConfiguration)
            return source
        }
    }

    @Test
    fun `CORS when disabled then response does not include CORS header`() {
        this.spring.register(CorsDisabledConfig::class.java).autowire()

        this.mockMvc.get("/")
        {
            header(HttpHeaders.ORIGIN, "https://example.com")
        }.andExpect {
            header { doesNotExist("Access-Control-Allow-Origin") }
        }
    }

    @EnableWebMvc
    @EnableWebSecurity
    open class CorsDisabledConfig : WebSecurityConfigurerAdapter() {
        override fun configure(http: HttpSecurity) {
            http.cors()
            http {
                cors {
                    disable()
                }
            }
        }

        @Bean
        open fun corsConfigurationSource(): CorsConfigurationSource {
            val source = UrlBasedCorsConfigurationSource()
            val corsConfiguration = CorsConfiguration()
            corsConfiguration.allowedOrigins = listOf("*")
            corsConfiguration.allowedMethods = listOf(
                    RequestMethod.GET.name,
                    RequestMethod.POST.name)
            source.registerCorsConfiguration("/**", corsConfiguration)
            return source
        }
    }

    @Test
    fun `CORS when CORS configuration source dsl then responds with CORS header`() {
        this.spring.register(CorsCrossOriginBeanConfig::class.java).autowire()

        this.mockMvc.get("/")
        {
            header(HttpHeaders.ORIGIN, "https://example.com")
        }.andExpect {
            header { exists("Access-Control-Allow-Origin") }
        }
    }

    @EnableWebMvc
    @EnableWebSecurity
    open class CorsCrossOriginSourceConfig : WebSecurityConfigurerAdapter() {
        override fun configure(http: HttpSecurity) {
            val source = UrlBasedCorsConfigurationSource()
            val corsConfiguration = CorsConfiguration()
            corsConfiguration.allowedOrigins = listOf("*")
            corsConfiguration.allowedMethods = listOf(
                    RequestMethod.GET.name,
                    RequestMethod.POST.name)
            source.registerCorsConfiguration("/**", corsConfiguration)
            http {
                cors {
                    configurationSource = source
                }
            }
        }
    }
}
