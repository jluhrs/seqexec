
name := Settings.Definitions.name

scalaVersion in ThisBuild := Settings.LibraryVersions.scala

scalacOptions in ThisBuild ++= Settings.Definitions.scalacOptions

organization in Global := "edu.gemini.ocs"

// Gemini repository
resolvers in ThisBuild += "Gemini Repository" at "https://github.com/gemini-hlsw/maven-repo/raw/master/releases"

def preventPublication(p: Project) =
  p.settings(
    publish := {},
    publishLocal := {},
    publishArtifact := false,
    publishTo := Some(Resolver.file("Unused transient repository", target.value / "fakepublish")),
    packagedArtifacts := Map.empty)

lazy val seqexec_server = preventPublication(project.in(file("app/seqexec-server")))
  .dependsOn(edu_gemini_seqexec_web_server)
  .aggregate(edu_gemini_seqexec_web_server)
  .enablePlugins(JavaServerAppPackaging)
  .settings(
    description := "Seqexec server for local testing",
    name := "seqexec-server",
    mainClass in Compile := Some("edu.gemini.seqexec.web.server.http4s.WebServerLauncher"),

    // No javadocs
    mappings in (Compile, packageDoc) := Seq(),

    // Don't create launchers for Windows
    makeBatScript := None,
    bashScriptConfigLocation := Some("${app_home}/../conf/config.properties"),

    // Run full opt js on the javascript. They will be placed on the "seqexec" jar
    resources in Compile += (fullOptJS in (edu_gemini_seqexec_web_client, Compile)).value.data,
    resources in Compile += (packageMinifiedJSDependencies in (edu_gemini_seqexec_web_client, Compile)).value,
    resources in Compile += (fullOptJS in (edu_gemini_seqexec_web_client_cli, Compile)).value.data,
    resources in Compile += (packageMinifiedJSDependencies in (edu_gemini_seqexec_web_client_cli, Compile)).value,

    //  Generate a custom logging.properties for the application
    mappings in Universal += {
      val f = generateLoggingConfigTask(LogType.ConsoleAndFiles).value
      f -> ("conf/" + f.getName)
    },

    // Put the jar files in the lib dir
    mappings in Universal <+= (packageBin in Compile) map { jar =>
      jar -> ("lib/" + jar.getName)
    },

    // Create a log dir
    mappings in Universal ++= Seq(file("log") -> "log"),

    // Launch options
    javaOptions in Universal ++= Seq(
      // -J params will be added as jvm parameters
      "-J-Xmx512m",
      "-J-Xms256m",

      // others will be added as app parameters
      // TODO Define how to configure applications
      "prod" // Run in production mode.
    )
  )
