

object VersionHelper {

  /**
   * Creates a version suffix based on the git branch
   */

  private val currentBranch = System.getenv("TRAVIS_BRANCH") match {
    case br if  br!= null && br.length > 0 => br
    case _ => GitHelper.abbrevRefProcess.lines.head
  }

  def versionSuffix(): String = currentBranch match {
    case "master" => ""
    case "develop" => s"-dev.${GitHelper.headSha()}"
    case b if b.startsWith("release/") => s"-rc.${GitHelper.headSha()}"
    case _ => s"-experimental.${GitHelper.headSha()}"
  }

}