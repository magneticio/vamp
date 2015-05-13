package io.vamp.core.cli


trait Parameters extends CommandLineBasics {

  type OptionMap = Map[Symbol, String]

  val host = 'host
  val deployment = 'deployment
  val cluster = 'cluster
  val command = 'command
  val deployable = 'deployable
  val name = 'name
  val source = 'source
  val destination = 'destination
  val help = 'help

  protected def readParameters(args: Array[String]): OptionMap = {
    val env_host = sys.env("VAMP_HOST")
    if (env_host.nonEmpty)
      nextOption(Map('host -> env_host), args.toList)
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
      case Nil => map
      case "--host" :: value :: tail => nextOption(map ++ Map(host -> value), tail)
      case "--deployment" :: value :: tail => nextOption(map ++ Map(deployment -> value), tail)
      case "--cluster" :: value :: tail => nextOption(map ++ Map(cluster -> value), tail)
      case "--deployable" :: value :: tail => nextOption(map ++ Map(deployable -> value), tail)
      case "--help" :: tail => nextOption(map ++ Map(help -> ""), tail)
      case "--destination" :: value :: tail => nextOption(map ++ Map(destination -> value), tail)

      case string :: opt2 :: tail if isSwitch(opt2) => nextOption(map ++ Map(name -> string), list.tail)
      case string :: Nil => nextOption(map ++ Map(name -> string), list.tail)
      case option :: tail => terminateWithError("Unknown option " + option)
        Map.empty
    }
  }

}
