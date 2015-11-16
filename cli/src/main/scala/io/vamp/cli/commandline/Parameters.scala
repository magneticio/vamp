package io.vamp.cli.commandline

trait Parameters extends CommandLineBasics {

  type OptionMap = Map[Symbol, String]

  val host = 'host
  val deployment = 'deployment
  val cluster = 'cluster
  val command = 'command
  val deployable = 'deployable
  val deployments = 'deployments
  val name = 'name
  val source = 'source
  val destination = 'destination
  val help = 'help
  val file = 'file
  val json = 'json
  val routing = 'routing
  val scale = 'scale
  val blueprint = 'blueprint
  val stdin = 'stdin
  val breed = 'breed
  val sla = 'sla
  val endpoint = 'endpoint
  val environment = 'environment
  val minimum = 'minimum
  val maximum = 'maximum
  val scale_by = 'scaleBy
  val target_cluster = 'targetCluster
  val upper = 'upper
  val lower = 'lower
  val interval = 'interval
  val cooldown = 'cooldown
  val as_blueprint = 'as_blueprint

  val VAMP_HOST = "VAMP_HOST"

  protected def readParameters(args: Array[String]): OptionMap = {
    if (sys.env.contains(VAMP_HOST))
      nextOption(Map('host -> sys.env(VAMP_HOST)), args.toList)
    else
      nextOption(Map(), args.toList)
  }

  protected def getParameter(key: Symbol)(implicit options: Map[Symbol, String]): String = {
    if (!options.contains(key)) terminateWithError(s"Parameter $key missing")
    options.get(key).get
  }

  protected def getOptionalParameter(key: Symbol)(implicit options: Map[Symbol, String]): Option[String] = {
    options.get(key)
  }

  protected def nextOption(map: OptionMap, list: List[String]): OptionMap = {
    def isSwitch(s: String) = s(0) == '-'
    list match {
      case Nil                                 ⇒ map
      case "--host" :: value :: tail           ⇒ nextOption(map ++ Map(host -> value), tail)
      case "--blueprint" :: value :: tail      ⇒ nextOption(map ++ Map(blueprint -> value), tail)
      case "--deployment" :: value :: tail     ⇒ nextOption(map ++ Map(deployment -> value), tail)
      case "--cluster" :: value :: tail        ⇒ nextOption(map ++ Map(cluster -> value), tail)
      case "--routing" :: value :: tail        ⇒ nextOption(map ++ Map(routing -> value), tail)
      case "--environment" :: value :: tail    ⇒ nextOption(map ++ Map(environment -> value), tail)
      case "--endpoint" :: value :: tail       ⇒ nextOption(map ++ Map(endpoint -> value), tail)
      case "--scale" :: value :: tail          ⇒ nextOption(map ++ Map(scale -> value), tail)
      case "--sla" :: value :: tail            ⇒ nextOption(map ++ Map(sla -> value), tail)
      case "--breed" :: value :: tail          ⇒ nextOption(map ++ Map(breed -> value), tail)
      case "--deployable" :: value :: tail     ⇒ nextOption(map ++ Map(deployable -> value), tail)
      case "--help" :: tail                    ⇒ nextOption(map ++ Map(help -> ""), tail)
      case "--routing" :: tail                 ⇒ nextOption(map ++ Map(routing -> ""), tail)
      case "--scale" :: tail                   ⇒ nextOption(map ++ Map(scale -> ""), tail)
      case "--destination" :: value :: tail    ⇒ nextOption(map ++ Map(destination -> value), tail)
      case "--file" :: value :: tail           ⇒ nextOption(map ++ Map(file -> value), tail)
      case "--json" :: tail                    ⇒ nextOption(map ++ Map(json -> "true"), tail)
      case "--as_blueprint" :: tail            ⇒ nextOption(map ++ Map(as_blueprint -> "true"), tail)
      case "--stdin" :: tail                   ⇒ nextOption(map ++ Map(stdin -> "true"), tail)
      case "--name" :: value :: tail           ⇒ nextOption(map ++ Map(name -> value), tail)
      case "--minimum" :: value :: tail        ⇒ nextOption(map ++ Map(minimum -> value), tail)
      case "--maximum" :: value :: tail        ⇒ nextOption(map ++ Map(maximum -> value), tail)
      case "--scale_by" :: value :: tail       ⇒ nextOption(map ++ Map(scale_by -> value), tail)
      case "--target_cluster" :: value :: tail ⇒ nextOption(map ++ Map(target_cluster -> value), tail)
      case "--upper" :: value :: tail          ⇒ nextOption(map ++ Map(upper -> value), tail)
      case "--lower" :: value :: tail          ⇒ nextOption(map ++ Map(lower -> value), tail)
      case "--interval" :: value :: tail       ⇒ nextOption(map ++ Map(interval -> value), tail)
      case "--cooldown" :: value :: tail       ⇒ nextOption(map ++ Map(cooldown -> value), tail)

      case option :: tail if isSwitch(option) ⇒
        terminateWithError("Unknown option " + option, Map.empty)
      case string :: tail ⇒
        if (!map.contains(name)) {
          nextOption(map ++ Map(name -> string), list.tail)
        } else {
          terminateWithError(s"Second name found with value '$string'; already had value '${map.getOrElse(name, "")}'", Map.empty)
        }
    }
  }

}
