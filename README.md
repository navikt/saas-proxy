# saas-proxy
API for saas for å nå interna nav-apier i google cloud (enten app eller pub.nais.io ingress). 
Proxyen slipper kun gjennom hvitelistede anrop med et gyldig azure token.

Den videresender forespørselen enten til pub.nais.io ingress eller til destinasjonsappen via kortversjonen av tjenesteoppdagelsesurlen, ref nais doc [her](https://doc.nais.io/clusters/service-discovery/?h=discovery#short-names)

> [!TIP]
> ### Sjekkliste for eksponering av nye endepunkter.
> 1. Inbound rules til ny app oppdateras av appeier saas-proxy
> 2. Outbound rules/external til ny app/ingress oppdateras i denne Saas-proxyen
> 3. Hvitelisten oppdateras.

Det må leggas til inbound rules i den app som ska exponeras:
```
- application: saas-proxy
  namespace: teamcrm
  cluster: <dev/prod>-gcp # Endast nødvendig mot pub.nais.io ingress
```
Samt outbound rule (om app) eller outbound external (om ingress) her i [dev.yml](https://github.com/navikt/saas-proxy/blob/master/.nais/dev.yaml) og [prod.yml](https://github.com/navikt/saas-proxy/blob/master/.nais/prod.yaml):
```
# App i gcp:
- application: <app>
  namespace: <namespace>
```

```
# pub.nais.io ingress:
- host: <ingress.to.endpoint-pub.nais.io>
```
Dette setter nettverkspolicyen slik at saas-proxyen kan kommunisere med appen, og forhåndsautoriserer azure-AD-klienten til proxyn.
Se dokumentasjon for nais [Access policies](https://doc.nais.io/nais-application/access-policy/) og [Pre-authorization](https://doc.nais.io/security/auth/azure-ad/access-policy/#pre-authorization)

Du legger til de endepunkter du vil gjøre tilgjengelig i hvitelisten før hvert miljø. Se
[dev.json](https://github.com/navikt/saas-proxy/blob/master/src/main/resources/whitelist/dev.json)
og
[prod.json](https://github.com/navikt/saas-proxy/blob/master/src/main/resources/whitelist/dev.json)

Hvitelisten er strukturert under *"namespace"* *"app"* *"pattern"*, der *"pattern"* er en streng bestående av http-metoden og regulære uttrykk før path, f.eks:
```
"teamnamespace": {
  "app": [
    "GET /getcall",
    "POST /done",
    "GET /api/.*"
  ]
}
```

### Test aktive hvitlisteregler
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

### Bruk av proxyn

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
