## Dependencies

<details>
<summary><strong>`no.nav.security:token-validation-core:5.0.29`</strong></summary>

## token-validation-core

**Opprinnelse:** no.nav.security:token-validation-core  
**Formål:** Validere tilgangstokener (access tokens) for sikker API-tilgang.  
**Bruk:** Sikrer at innkommende forespørsler har gyldige tokens, spesielt for NAVs sikkerhet.  
**Motivasjon:** Beskytter API-er mot uautorisert tilgang.  
**Alternativer:** Auth0, Keycloak token validatorer  
**Hvorfor valgt:** NAVs egen sikkerhetsbibliotek tilpasset interne behov.
</details>

<details>
<summary><strong>`org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1`</strong></summary>

## kotlinx-coroutines-core

**Opprinnelse:** org.jetbrains.kotlinx:kotlinx-coroutines-core  
**Formål:** Asynkron programmering og korutiner i Kotlin.  
**Bruk:** Forenkler asynkrone operasjoner og parallell kjøring i appen.  
**Motivasjon:** Moderne Kotlin-standard for concurrency.  
**Alternativer:** RxJava, Java Futures  
**Hvorfor valgt:** Kotlin-først og lett å bruke.
</details>

<details>
<summary><strong>`org.http4k:http4k-core:5.14.4.0`</strong></summary>

## http4k-core

**Opprinnelse:** org.http4k:http4k-core  
**Formål:** Kjernen i http4k rammeverket for funksjonell HTTP-programmering.  
**Bruk:** Grunnlag for bygging av HTTP-tjenester i Kotlin.  
**Motivasjon:** Funksjonell tilnærming, lettvekts og modulær.  
**Alternativer:** Spring WebFlux, Ktor  
**Hvorfor valgt:** Enkelhet og Kotlin-first design.
</details>

<details>
<summary><strong>`org.http4k:http4k-server-netty:5.47.0.0`</strong></summary>

## http4k-server-netty

**Opprinnelse:** org.http4k:http4k-server-netty  
**Formål:** Netty-basert HTTP-serveradapter for http4k.  
**Bruk:** Kjører http4k applikasjoner på Netty-server.  
**Motivasjon:** Rask, asynkron HTTP-server med lavt minneforbruk.  
**Alternativer:** Jetty, Undertow  
**Hvorfor valgt:** God ytelse og enkel integrasjon med http4k.
</details>

<details>
<summary><strong>`io.netty:netty-handler:4.1.118.Final`</strong></summary>

## netty-handler

**Opprinnelse:** io.netty:netty-handler  
**Formål:** Netty-modul for protokollhåndtering og pipeline.  
**Bruk:** Brukes internt av http4k og Netty-server for håndtering av nettverkspakker.  
**Motivasjon:** Modularisering og utvidbarhet i Netty.  
**Alternativer:** Ingen, spesifikk for Netty.  
**Hvorfor valgt:** Påkrevd for Netty-basert HTTP-server.
</details>

<details>
<summary><strong>`io.netty:netty-common:4.1.118.Final`</strong></summary>

## netty-common

**Opprinnelse:** io.netty:netty-common  
**Formål:** Fellesverktøy og basiskomponenter for Netty.  
**Bruk:** Delte funksjoner brukt av flere Netty-moduler.  
**Motivasjon:** Grunnleggende infrastruktur for Netty.  
**Alternativer:** Ingen  
**Hvorfor valgt:** Påkrevd avhengighet for Netty.
</details>

<details>
<summary><strong>`org.http4k:http4k-client-okhttp:5.14.4.0`</strong></summary>

## http4k-client-okhttp

**Opprinnelse:** org.http4k:http4k-client-okhttp  
**Formål:** OkHttp-klientadapter for http4k.  
**Bruk:** Gjør det mulig å sende HTTP-forespørsler via OkHttp i http4k klienter.  
**Motivasjon:** Kombinerer http4k funksjonalitet med OkHttp sine ytelsesfordeler.  
**Alternativer:** http4k-client-apache, Ktor client  
**Hvorfor valgt:** Lettvekts, godt støttet HTTP-klient.
</details>

<details>
<summary><strong>`io.github.microutils:kotlin-logging:3.0.5`</strong></summary>

## kotlinter

**Opprinnelse:** org.jmailen.kotlinter:org.jmailen.kotlinter  
**Formål:** Kotlin-kodelinter og formattering.  
**Bruk:** Sørger for automatisk kodelinting i byggeprosessen.  
**Motivasjon:** Sikre kodekvalitet og enhetlig kodeformat.  
**Alternativer:** Manuell kjøring av ktlint CLI.  
**Hvorfor valgt:** Enkel integrasjon med Gradle, automatisering av linting.
</details>

<details>
<summary><strong>`ch.qos.logback:logback-classic:1.5.20`</strong></summary>

## logback-classic

**Opprinnelse:** ch.qos.logback:logback-classic  
**Formål:** Kraftig, konfigurerbar og rask logging.  
**Bruk:** Standard logging-rammeverk i mange Kotlin/Java prosjekter.  
**Motivasjon:** Pålitelig og godt støttet.  
**Alternativer:** Log4j, java.util.logging  
**Hvorfor valgt:** Standardvalg i mange Kotlin/Java prosjekter.
</details>

<details>
<summary><strong>`net.logstash.logback:logstash-logback-encoder:8.1`</strong></summary>

## logstash-logback-encoder

**Opprinnelse:** net.logstash.logback:logstash-logback-encoder  
**Formål:** Logback-modul for JSON-formatert logging, kompatibel med Logstash.  
**Bruk:** Formaterer logger for strukturert logging og integrasjon med ELK-stack.  
**Motivasjon:** Forbedrer logging for analyse og feilsøking.  
**Alternativer:** Log4j JSON appender  
**Hvorfor valgt:** Sømløs integrasjon med Logback og ELK.
</details>

<details>
<summary><strong>`io.prometheus:simpleclient_common:0.8.1`</strong></summary>

## simpleclient_common

**Opprinnelse:** io.prometheus:simpleclient_common  
**Formål:** Felles komponenter for Prometheus klientbiblioteker.  
**Bruk:** Grunnleggende funksjoner for Prometheus metrikker.  
**Motivasjon:** Gjenbrukbar infrastruktur.  
**Alternativer:** Ingen  
**Hvorfor valgt:** Påkrevd for Prometheus integrasjon.
</details>

<details>
<summary><strong>`io.prometheus:simpleclient_hotspot:0.8.1`</strong></summary>

## simpleclient_hotspot

**Opprinnelse:** io.prometheus:simpleclient_hotspot  
**Formål:** Prometheus client for Hotspot JVM-metrikker.  
**Bruk:** Eksponere JVM helse- og ytelsesdata.  
**Motivasjon:** Overvåkning av applikasjonsytelse.  
**Alternativer:** Micrometer  
**Hvorfor valgt:** Direkte Prometheus støtte.
</details>

## Plugins

<details>
<summary><strong>`org.jetbrains.kotlin.jvm:1.9.24`</strong></summary>

## kotlin-jvm

**Opprinnelse:** org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm  
**Formål:** Kompilere Kotlin-kode til JVM bytekode.  
**Bruk:** Legger til Kotlin/JVM støtte i Gradle-bygget.  
**Motivasjon:** Offisiell plugin, bred støtte og vedlikehold.  
**Alternativer:** Kotlin multiplatform plugin.  
**Hvorfor valgt:** Standard for Kotlin/JVM prosjekter.
</details>

<details>
<summary><strong>`org.jmailen.kotlinter:3.2.0`</strong></summary>

## kotlinter

**Opprinnelse:** Gradle plugin for ktlint  
**Formål:** Kotlin-kodelinter og formattering  
**Bruk:** Sørger for automatisk kodelinting i byggeprosessen  
**Motivasjon:** Sikre kodekvalitet og enhetlig kodeformat  
**Alternativer:** Manuell kjøring av ktlint CLI  
**Hvorfor valgt:** Enkel integrasjon med Gradle, automatisering av linting
</details>

<details>
<summary><strong>`com.gradleup.shadow:8.3.1`</strong></summary>

## shadow

**Opprinnelse:** com.gradleup:shadow  
**Formål:** Gradle plugin for å lage "fat" eller "uber" JAR-filer som inkluderer alle avhengigheter.  
**Bruk:** Pakker applikasjon og alle nødvendige biblioteker i én JAR for enkel distribusjon.  
**Motivasjon:** Forenkler distribusjon og kjøring av applikasjoner uten behov for å håndtere avhengigheter separat.  
**Alternativer:** Shadow plugin fra John Engelman (org.gradle.plugins.shadow), Spring Boot plugin  
**Hvorfor valgt:** Modifisert og oppdatert versjon med ekstra funksjonalitet og forbedringer i forhold til eldre shadow-plugins.
</details>

