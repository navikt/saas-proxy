# saas-proxy
API for saas for å nå interna nav-apier i google cloud (enten app eller pub.nais.io ingress). 
Proxyen slipper kun gjennom hvitelistede anrop med et gyldig azure token.

Den videresender forespørselen enten til pub.nais.io ingress eller til destinasjonsappen via kortversjonen av tjenesteoppdagelsesurlen, ref nais doc [her](https://doc.nais.io/clusters/service-discovery/?h=discovery#short-names)

> [!TIP]
> ### Sjekkliste for eksponering av nye endepunkter.
> #### Hvis app er i GCP
> 1. Inbound rules i ny app oppdateras av appeier med saas-proxy
> 2. Outbound rules til ny app oppdateras i denne Saas-proxyen
> 3. Hvitelisten oppdateras.
> #### Hvis app er i FSS
> 1. Inbound rules i ny app oppdateras av appeier med saas-proxy
> 2. Outbound external til pub.nais.io ingress oppdateras i denne Saas-proxyen
> 3. Ingresslisten oppdateras
> 4. Hvitelisten oppdateras.


<details>
<summary><b>Konfigurasjon hvis appen som skal eksponeres er i GCP</b></summary>
  
Det må leggas til inbound rules i den app som ska exponeras av appeier:
```
- application: saas-proxy
  namespace: teamcrm
```

Samt outbound rule her i [.nais/dev.yml](https://github.com/navikt/saas-proxy/blob/master/.nais/dev.yaml) og [.nais/prod.yml](https://github.com/navikt/saas-proxy/blob/master/.nais/prod.yaml):
```
# App i gcp:
- application: <app>
  namespace: <namespace>
```

Dette setter nettverkspolicyen slik at saas-proxyen kan kommunisere med appen, og forhåndsautoriserer azure-AD-klienten til proxyn.
Se dokumentasjon for nais [Access policies](https://doc.nais.io/nais-application/access-policy/) og [Entra-ID](https://doc.nais.io/auth/entra-id/)

Du legger til de endepunkter du vil gjøre tilgjengelig i hvitelisten før hvert miljø. Se
[whitelist/dev.json](https://github.com/navikt/saas-proxy/blob/master/src/main/resources/whitelist/dev.json)
og
[whitelist/prod.json](https://github.com/navikt/saas-proxy/blob/master/src/main/resources/whitelist/prod.json)

Hvitelisten er strukturert under *"namespace"* *"app"* *"pattern"*, der *"pattern"* er en streng bestående av http-metoden og regulære uttrykk før path og ev. scope, f.eks:
```
"teamnamespace": {
  "app": [
    "GET /getcall",
    "POST /done",
    "GET /api/.*"
    "GET /scoped/api/.* scope:nameofscope"
  ]
}
```
</details>

<details>
<summary><b>Konfigurasjon hvis appen som skal eksponeres er i FSS med en pub.nais.io ingress</b></summary>

  
Det må leggas til inbound rules i den app som ska exponeras av appeier:
```
- application: saas-proxy
  namespace: teamcrm
  cluster: <dev/prod>-gcp
```

Samt outbound external her i [.nais/dev.yml](https://github.com/navikt/saas-proxy/blob/master/.nais/dev.yaml) og [.nais/prod.yml](https://github.com/navikt/saas-proxy/blob/master/.nais/prod.yaml):
```
- host: <ingress.to.endpoint-pub.nais.io>
```

Du legger også til ingressen du vil gjøre tilgjengelig i ingresslisten før hvert miljø. Se
[ingresses/dev.json](https://github.com/navikt/saas-proxy/blob/master/src/main/resources/ingresses/dev.json)
og
[ingresses/prod.json](https://github.com/navikt/saas-proxy/blob/master/src/main/resources/ingresses/prod.json)

Dette setter nettverkspolicyen slik at saas-proxyen kan kommunisere med appen, og forhåndsautoriserer azure-AD-klienten til proxyn.
Se dokumentasjon for nais [Access policies](https://doc.nais.io/nais-application/access-policy/) og [Entra-ID](https://doc.nais.io/auth/entra-id/)

Du legger til de endepunkter du vil gjøre tilgjengelig i hvitelisten før hvert miljø. Se
[whitelist/dev.json](https://github.com/navikt/saas-proxy/blob/master/src/main/resources/whitelist/dev.json)
og
[whitelist/prod.json](https://github.com/navikt/saas-proxy/blob/master/src/main/resources/whitelist/prod.json)

Hvitelisten er strukturert under *"namespace"* *"app"* *"pattern"*, der *"pattern"* er en streng bestående av http-metoden og regulære uttrykk før path og ev. scope, f.eks:
```
"teamnamespace": {
  "app": [
    "GET /getcall",
    "POST /done",
    "GET /api/.*"
    "GET /scoped/api/.* scope:nameofscope"
  ]
}
```
</details>

<details>
<summary><b>Teste aktive hvitlisteregler</b></summary>
Du kan teste om ett anrop er bestått eller ikke mot aktive regler hvis du går imot

https://saas-proxy.dev.intern.nav.no/internal/test/<uri-du-vil-testa>

https://saas-proxy.intern.nav.no/internal/test/<uri-du-vil-testa>

med header **target-app** (o optional ***target-namespace***) med appen du ønsker nå.
Ex:
```
curl https://saas-proxy.intern.dev.nav.no/internal/test/v1/oppfolging/periode -H "target-app:veilarbapi"
Report:
Evaluating GET /v1/oppfolging/periode on method GET, path /v1/oppfolging/periode true
Evaluating GET /v1/oppfolging/info on method GET, path /v1/oppfolging/periode false
Approved
```

</details>

<details>
<summary><b>Bruk av proxyn</b></summary>

De eksterna klientene som ønsker anrope via proxyen må sende med tre headers:

**target-app** - den app de ønsker nå (ex. sf-brukernotifikasjon)

**Authorization** - azure token mot saas-proxy

***target-namespace (optional)*** - eksplisitt namespace i tilfelle det er to apper i hvitelisten med identiske navn under forskjellige namespace

De bruker samme metode og uri som om de skulle anrope en ingress till den interne appen, men ingressen til proxyn (dev: https://saas-proxy.ekstern.dev.nav.no, prod: https://saas-proxy.nav.no)

Eks:

```
https://sf-brukernotifikasjon-v2.dev.intern.nav.no/do/a/call?param=1
```
blir
```
https://saas-proxy.ekstern.dev.nav.no/do/a/call?param=1
```
NB En app i gcp trenger ikke ha en ingress for å være tilgjengelig via proxy

</details>

<details>
<summary><strong>Flyt</strong></summary>

[![](https://mermaid.ink/img/pako:eNp9k81u2zAMx19F4KkFHMMfieP5UKBol2GHFsUM5DD4wthMItSWPEle0wW57gH2iHuSSU7cOBtWwwdJ_JF_kqL2UMqKIANN3zoSJd1z3ChsCtGiMrzkLQrD8gVDzXKsSa-lKold0c6QElhfX4IfhVHo2AdeKqnlejj6fH8JPim5e3WgRtST1u0ugdu2vatlVznm8Xbp9uzqk5Sbmlhv-Ev5qVs9ItcOb7uVL-za55L9_vmLSWEFqGHYtoUoBLNfvpjc3PSZZWxBptwyLEvSmhn5TGOmzzNjOYmKKdcibdgLN9sx2DPneEuseYWGBuQIYd3XxLhg4yqOxnGUoXCbmFQvqN50z-iATM4JfiHdSqHpCFGtqVcbSv_OcdyWf1VP7XtH9ET8X1NUxUVDLJgvHGU6JWy8AQYPNopXkBnVkQcNqQbdFvbOuwCzpYYKyOyyojV2tSnAG5mWqDiu7Cg6Zn9ULGCF5fNGyc5m0bvaqxDaDggJ6-6gQyEOVtoOy1cpm0Hdemy2kK3RdsyDrnUXd3oCb6c2RkXqzsY2kM2StA8C2R52kMVx5E_DYDafR2kwjYNp4sErZEnop2E6C9P5LIrSZP7h4MGPXjbw0ziIkiAN4yhO7G8dqOJGqofjQ-zf4-EPJ1cwvQ?type=png)](https://mermaid.live/edit#pako:eNp9k81u2zAMx19F4KkFHMMfieP5UKBol2GHFsUM5DD4wthMItSWPEle0wW57gH2iHuSSU7cOBtWwwdJ_JF_kqL2UMqKIANN3zoSJd1z3ChsCtGiMrzkLQrD8gVDzXKsSa-lKold0c6QElhfX4IfhVHo2AdeKqnlejj6fH8JPim5e3WgRtST1u0ugdu2vatlVznm8Xbp9uzqk5Sbmlhv-Ev5qVs9ItcOb7uVL-za55L9_vmLSWEFqGHYtoUoBLNfvpjc3PSZZWxBptwyLEvSmhn5TGOmzzNjOYmKKdcibdgLN9sx2DPneEuseYWGBuQIYd3XxLhg4yqOxnGUoXCbmFQvqN50z-iATM4JfiHdSqHpCFGtqVcbSv_OcdyWf1VP7XtH9ET8X1NUxUVDLJgvHGU6JWy8AQYPNopXkBnVkQcNqQbdFvbOuwCzpYYKyOyyojV2tSnAG5mWqDiu7Cg6Zn9ULGCF5fNGyc5m0bvaqxDaDggJ6-6gQyEOVtoOy1cpm0Hdemy2kK3RdsyDrnUXd3oCb6c2RkXqzsY2kM2StA8C2R52kMVx5E_DYDafR2kwjYNp4sErZEnop2E6C9P5LIrSZP7h4MGPXjbw0ziIkiAN4yhO7G8dqOJGqofjQ-zf4-EPJ1cwvQ)

</details

[Dependencies](dependencies.md)
