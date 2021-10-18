package com.lynas.springoauth2poc

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.ClientRegistrations
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import java.util.*


@SpringBootApplication
class SpringOAuth2PocApplication

fun main(args: Array<String>) {
    runApplication<SpringOAuth2PocApplication>(*args)
}


@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableWebSecurity
class AppSecurityConfig : WebSecurityConfigurerAdapter() {

    override fun configure(http: HttpSecurity) {
        http.authorizeRequests().anyRequest().authenticated()
            .and()
            .oauth2Login {
                it.userInfoEndpoint { u ->
                    u.userService(oAuth2UserService())
                }
            }
            .logout {
                it.logoutSuccessHandler()
            }
    }

    private fun oidcLogoutSuccessHandler(): LogoutSuccessHandler {
        val oidcLogoutSuccessHandler = OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository)
        // Sets the location that the End-User's User Agent will be redirected to
        // after the logout has been performed at the Provider
        oidcLogoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}")
        return oidcLogoutSuccessHandler
    }

    @Bean
    fun oAuth2UserService(): OAuth2UserService<OAuth2UserRequest, OAuth2User> {
        val delegate = DefaultOAuth2UserService()
        return OAuth2UserService { userRequest ->
            var oauth2User = delegate.loadUser(userRequest)
            val accessToken = userRequest.accessToken.tokenValue
            oauth2User = DefaultOAuth2User(getRolesFromToken(accessToken), oauth2User.attributes, "sub")
            oauth2User
        }
    }

    @Bean
    fun clientRegistrationRepository(): ClientRegistrationRepository {
        val clientRegistration: ClientRegistration = ClientRegistrations
            .fromIssuerLocation("http://localhost:8080/auth/realms/demo")
            .clientId("app-demo")
            .clientSecret("e3f519b4-0272-4261-9912-8b7453ac4ecd")
            .build()
        return InMemoryClientRegistrationRepository(clientRegistration)
    }
}

@Controller
class DemoController {

    @GetMapping("/private")
    suspend fun private(
        @RegisteredOAuth2AuthorizedClient authorizedClient: OAuth2AuthorizedClient,
        @AuthenticationPrincipal oauth2User: OAuth2User
    ): String {
        return "private"
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @GetMapping("/private/admin")
    suspend fun privateAdmin(
        @RegisteredOAuth2AuthorizedClient authorizedClient: OAuth2AuthorizedClient,
        @AuthenticationPrincipal oauth2User: OAuth2User
    ): String {
        return "private"
    }

    @GetMapping("/public")
    suspend fun public() = "public"

}


fun getRolesFromToken(token: String): HashSet<GrantedAuthority> {
    val chunks = token.split(".");
    val decoder = Base64.getDecoder();
    val payload = String(decoder.decode(chunks[1]))
    val map = ObjectMapper().readValue<MutableMap<String, Any>>(payload)
    val ra = map["resource_access"] as Map<String, Any>
    val ad = ra["app-demo"] as Map<String, String>
    val roles = ad["roles"] as ArrayList<String>
    return roles.map { "ROLE_${it.uppercase()}" }.map { SimpleGrantedAuthority(it) }.toHashSet()

}