import sbt._

object GitHelper {

  val HeadRefsPattern = """refs/heads/(.*)\s""".r

  def symbolicRefProcess = Process("git symbolic-ref HEAD")

  def abbrevRefProcess = Process("git rev-parse --abbrev-ref HEAD")

  def headShaProcess = Process("git rev-parse --short HEAD")

  def headSha(): String = headShaProcess.!!.stripLineEnd

  def currentBranch = Process("git branch 2> /dev/null | sed -e '/^[^*]/d' -e 's/* \\(.*\\)/\\1/'")

  def commitRangeCount(start: String, end: String): Int =
    Process(s"git rev-list ${start}..${end}").lines.size

  def revisionFromBranch(branch: String, commitish: String): Int = {
    abbrevRefProcess.!!.stripLineEnd match {
      case `branch` => 0
      case _ =>
        commitRangeCount(branch, commitish)
    }
  }
}