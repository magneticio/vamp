package io.vamp.core.container_driver.docker.wrapper.methods

import io.vamp.core.container_driver.docker.wrapper.Create.Response
import io.vamp.core.container_driver.docker.wrapper.json.RequestBody
import io.vamp.core.container_driver.docker.wrapper.model._
import io.vamp.core.container_driver.docker.wrapper.{Docker, Requests, model}

trait Containers extends Util {
  self: Requests =>


  object containers {
    private[this] def base = host / "containers"

    case class Containers() extends Docker.Completion[List[model.Container]] {

      def apply[T](handler: Docker.Handler[T]) = request(base / "json")(handler)
    }

    case class Create(private val _config: ContainerConfig, private val _name: Option[String] = None) extends Docker.Completion[Response] {

      def config(cfg: ContainerConfig) = copy(_config = cfg)

      def withConfig(f: ContainerConfig => ContainerConfig) = config(f(_config))

      def image(img: String) = withConfig(_.copy(image = img))

      def env(vars: (String, String)*) = withConfig(_.copy(env = vars.toMap))

      def exposedPorts(ports: String*) = withConfig(_.copy(exposedPorts = ports.toSeq))

      def name(n: String) = copy(_name = Some(n))

      def volumes(vx: String*) = withConfig(_.copy(volumes = vx.toSeq))

      def apply[T](handler: Docker.Handler[T]) =
        request(addContentType(base.POST) / "create" <<? _name.map("name" -> _) << body)(handler)

      def body = new RequestBody().requestCreate(_config, _name)

    }

    case class Container(id: String) extends Docker.Completion[ContainerDetails] {

      case class Start(_config: HostConfig) extends Docker.Completion[Unit] {

        def config(cfg: HostConfig) = copy(_config = cfg)

        def withConfig(f: HostConfig => HostConfig) = config(f(_config))

        def publishAllPorts(pub: Boolean) =
          withConfig(_.copy(publishAllPorts = pub))

        def portBind(containerPort: Port, binding: PortBinding*) =
          withConfig(_.copy(portBindings = _config.portBindings + (containerPort -> binding.toList)))

        def volumeBind(bindings: VolumeBinding*) = withConfig(_.copy(binds = bindings.toSeq))

        def volumesFrom(bindings: VolumeFromBinding*) = withConfig(_.copy(volumesFrom = bindings.toSeq))

        def cpuShares(cpu: Int) = withConfig(_.copy(cpuShares = cpu))

        def memory(mem: Long) = withConfig(_.copy(memory = mem))

        def memorySwap(swap: Long) = withConfig(_.copy(memorySwap = swap))

        def apply[T](handler: Docker.Handler[T]) =
          request(addContentType(base.POST) / id / "start" << body)(handler)

        def body = new RequestBody().requestStart(_config)
      }


      case class Kill() extends Docker.Completion[Unit] {

        def apply[T](handler: Docker.Handler[T]) = request(base.POST / id / "kill")(handler)
      }

      case class Delete() extends Docker.Completion[Unit] {

        def apply[T](handler: Docker.Handler[T]) = request(base.DELETE / id)(handler)

      }

      def apply[T](handler: Docker.Handler[T]) =
        request(base / id / "json")(handler)

      def start = Start(HostConfig())

      def kill = Kill()

      def delete = Delete()

    }

    def list = Containers()

    def create(image: String) = Create(ContainerConfig(image = image))

    def get(id: String) = Container(id)
  }

}
