# scala-oidc
Scala + OIDCの個人的な学習用Repository

# Startup
```
(sbt "clientServer/run -Dhttp.port=9000" &) && (sbt "authServer/run -Dhttp.port=9001" &) && (sbt "resourceServer/run -Dhttp.port=9002" &)
```