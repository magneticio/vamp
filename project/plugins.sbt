resolvers += Resolver.url(
  "bintray-sbt-plugin-releases",
  url("https://dl.bintray.com/content/sbt/sbt-plugin-releases"))(
    Resolver.ivyStylePatterns)

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")

addSbtPlugin("org.xerial.sbt" % "sbt-pack" % "0.8.0")

resolvers += Classpaths.sbtPluginReleases

resolvers += Classpaths.typesafeReleases

addSbtPlugin("com.typesafe.sbt" % "sbt-pgp" % "0.8.3")

addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.6.0")

addSbtPlugin("net.vonbuchholtz" % "sbt-dependency-check" % "0.1.1")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.2")

addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.1")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.5")
