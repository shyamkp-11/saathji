package app.ruhani.config

import app.ruhani.jwt.JwtAuthFilter
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter

@Configuration
@EnableWebSecurity
class SecurityConfig(private val jwtAuthFilter: JwtAuthFilter) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { }   // honor CorsConfigurationSource / WebConfig CORS so preflight isn't blocked
            .csrf { it.disable() }
            .anonymous { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // Defense-in-depth response headers. HSTS only takes effect over HTTPS;
            // it's harmless on HTTP and ready for the TLS-terminating proxy in prod.
            .headers { h ->
                h.httpStrictTransportSecurity { hsts ->
                    hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000)  // 1 year
                }
                h.frameOptions { it.deny() }
                h.contentTypeOptions { }
                h.referrerPolicy { it.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN) }
            }
            .exceptionHandling {
                it.authenticationEntryPoint { _, res, _ ->
                    res.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                }
                it.accessDeniedHandler { _, res, _ ->
                    res.sendError(HttpServletResponse.SC_FORBIDDEN)
                }
            }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    // Spring's BasicErrorController forwards to /error to render
                    // ResponseStatusException bodies — that internal dispatch
                    // hits the filter chain again, so it has to be permitAll
                    // or every 4xx becomes a 401.
                    .requestMatchers("/error").permitAll()
                    .requestMatchers("/auth/**").permitAll()
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers(HttpMethod.GET, "/posts/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/search").permitAll()
                    .requestMatchers(HttpMethod.GET, "/authors/*/posts").permitAll()
                    // Lists are public to browse / read; mutating endpoints
                    // (POST/PUT/DELETE under /lists) still require a JWT.
                    .requestMatchers(HttpMethod.GET, "/lists", "/lists/*").permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
