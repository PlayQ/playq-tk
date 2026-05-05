package net.playq.tk.util

import izumi.functional.bio.{F, IO2}

import java.io.{File, FileWriter}
import scala.collection.mutable.ListBuffer
import scala.sys.process.*
import scala.util.{Failure, Success, Try}

object process {
  final case class ProcessResult(output: String, exitCode: Int, err: Option[String]) {
    override def toString: String = {
      s"""Output: $output
         |Exit code: $exitCode
         |Error: $err
         |""".stripMargin
    }
  }

  def exec(cmd: String): ProcessResult                                      = runProcess(Process(cmd))
  def exec(cmd: Seq[String], cwd: Option[File] = None): ProcessResult       = runProcess(Process(cmd, cwd))
  def execBash[F[+_, +_]: IO2](script: String): F[Throwable, ProcessResult] = {
    ClosableFile.resourceTempFile("script", ".sh").use {
      scriptFile =>
        for {
          _   <- F.bracket(F.syncThrowable(new FileWriter(scriptFile.file)))(w => F.sync(w.close()))(w => F.sync(w.write(script)))
          _   <- F.sync(process.exec(Seq(s"chmod", "+x", scriptFile.absolutePath.toString)))
          res <- F.sync(process.exec(Seq(s"/bin/bash", "-c", scriptFile.absolutePath.toString)))
        } yield res
    }
  }

  private def runProcess(p: ProcessBuilder): ProcessResult = {
    Try {
      val out       = ListBuffer.empty[String]
      val err       = ListBuffer.empty[String]
      val exitValue = p.!(ProcessLogger(out.append, err.append))
      ProcessResult(out.mkString("\n"), exitValue, Option(err.mkString("\n")).filter(_.nonEmpty))
    } match {
      case Failure(exception) => ProcessResult("N/A", 1, Some(exception.getMessage))
      case Success(value)     => value
    }
  }
}
