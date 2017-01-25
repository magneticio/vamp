import scala.util.Try

object VersionHelper {
  def versionByTag: String = sys.env.getOrElse("VAMP_VERSION", Try(GitHelper.describe).getOrElse("katana"))
}