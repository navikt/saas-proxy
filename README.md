# saas-proxy
API for saas for å nå interna nav-apier i google cloud.
Proxyen slipper kun gjennom hvitelistede anrop med et gyldig azure token.

Du legger til de ingresser og endepunkter du vil gjøre tilgjengelig i hvitelisten før hvert miljø. Se
[dev.json](https://github.com/navikt/saas-proxy/blob/master/src/main/resources/whitelist/dev.json)
og
[prod.json](https://github.com/navikt/saas-proxy/blob/master/src/main/resources/whitelist/dev.json)

Hvitelisten er strukturert under *"team"* *"ingress"* *"pattern"*, der *"pattern"* er en streng bestående av http-metoden og regulære uttrykk før path, f.eks:
```
"GET /getcall",
"POST /done",
"GET /api/.*"
```

### Test aktive hvitlisteregler
Du kan teste om ett anrop er bestått eller ikke mot aktive regler hvis du går imot (eks med postman)

https://saas-proxy.dev.intern.nav.no/internal/test/<uri-du-vil-testa>

https://saas-proxy.intern.nav.no/internal/test/<uri-du-vil-testa>

med header **target-ingress** med ingressen du ønsker nå.

### Bruk av proxyn

De eksterna klientene som ønsker anrope via proxyen må sende med tre headers:

**target-ingress** - den ingress de ønsker nå (ex. https://sf-brukernotifikasjon-v2.dev.intern.nav.no)

**target-client-id** - azure client id før din app 

**Authorization** - azure token

De bruker samme metode og uri som om de skulle mot den interne ingressen, men ingressen til proxyn (dev: https://saas-proxy.ekstern.dev.nav.no, prod: https://saas-proxy.nav.no)

Eks:

```
https://sf-brukernotifikasjon-v2.dev.intern.nav.no/do/a/call?param=1
```
blir
```
https://saas-proxy.ekstern.dev.nav.no/do/a/call?param=1
```






