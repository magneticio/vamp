import scala.util.Try

object VersionHelper {
  def versionByTag: String = Try(GitHelper.describe).getOrElse("katana")
}