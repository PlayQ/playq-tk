package net.playq.tk.npmresolver

import io.circe.*
import io.circe.parser.*
import izumi.functional.bio.catz.BIOToAsync
import izumi.functional.bio.{Async2, BlockingIO2, F, Fork2, Primitives2, Temporal2}
import izumi.functional.lifecycle.Lifecycle
import net.playq.tk.compression.TkCompression
import net.playq.tk.http.TkHttp4sClient
import net.playq.tk.npmresolver.caches.ConcurrentActionCache
import net.playq.tk.util.ClosableFile
import org.http4s.circe.jsonOf
import org.http4s.{EntityDecoder, Method, Request, Uri}

import java.io.{File, FileOutputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

object NPMResolver {
  final case class Dist(npm: NPMDist, artifact: NPMArtifact)
  final case class ToDownload(tarballs: List[Dist])
}

class NPMResolver[F[+_, +_]: Async2: Fork2: Temporal2: BlockingIO2: Primitives2](
  client: TkHttp4sClient[F],
  compression: TkCompression[F],
  libsCache: NPMLibsCache[F],
) {
  type BIOTask[A] = F[Throwable, A]
  import NPMResolver.*

  def fetchArtifact(downloadDir: Path, artifact: NPMArtifact): F[Throwable, Map[String, NPMArtifactResolved]] = {
    for {
      deps        <- resolve(artifact)
      depsContent <- F
        .traverse(deps.tarballs)(
          dist => {
            for {
              maybeCached      <- libsCache.fetch(dist.artifact.name, dist.artifact.version)
              _                <- downloadModuleFiles(downloadDir, dist, maybeCached)
              pathToModule      = downloadDir.resolve(dist.artifact.name)
              packageInfo       = pathToModule.resolve("package.json")
              resolvedArtifact <- F.ifThenElse(Files.exists(packageInfo))(
                {
                  val content = Files.readAllBytes(packageInfo)
                  F.fromEither(parse(new String(content, StandardCharsets.UTF_8)))
                    .map(
                      json => {
                        NPMArtifactResolved(dist.artifact.name, getMainJsFilePath(pathToModule, json), isESM = isESModule(json))
                      }
                    )
                },
                F.sync(NPMArtifactResolved(dist.artifact.name, pathToModule.resolve("index.js"), isESM = false)), // if there is no package.json
              )
            } yield dist.artifact.name -> resolvedArtifact
          }
        ).map(_.toMap)
    } yield depsContent
  }

  private def isESModule(json: Json): Boolean = {
    json.hcursor.downField("type").as[String].exists(_ == "module")
  }

  private def getMainJsFilePath(baseDir: Path, json: Json): Path = {
    baseDir.resolve(
      json.hcursor
        .downField("main").as[String]
        .getOrElse(
          json.hcursor
            .downField("module").as[String].getOrElse("index.js")
        )
        .replaceFirst("""^\./""", "")
    )
  }

  def downloadModuleFiles(
    downloadDir: Path,
    tarball: Dist,
    maybeCached: Option[Array[Byte]],
  ): F[Throwable, List[File]] = {
    (maybeCached match {
      case Some(cached) =>
        createTarArchiveFromBytes(
          downloadDir.resolve(s"${tarball.artifact.name}-${tarball.artifact.version}.tgz").toFile,
          cached,
        )
      case None =>
        client
          .expectFile(
            Request(Method.GET, Uri.unsafeFromString(tarball.npm.tarball)),
            fileSuffix = ".tgz",
          ).evalTap(tgz => libsCache.put(tarball.artifact.name, tarball.artifact.version, Files.readAllBytes(tgz.absolutePath)))
    }).use {
      tgz =>
        val outputDir = downloadDir.resolve(tarball.artifact.name)
        compression.extractTarArchive(
          tgz.absolutePath,
          outputDir,
          None,
          _.replaceFirst("^package/", ""),
        )
    }
  }

  def resolve(artifact: NPMArtifact): F[Throwable, ToDownload] = {
    resolve(
      artifact,
      new ConcurrentActionCache[F, NPMArtifact, NPMDescriptor],
    ).map(td => td.copy(tarballs = td.tarballs.distinct))
  }

  private def createTarArchiveFromBytes(file: File, content: Array[Byte]): Lifecycle[F[Throwable, *], ClosableFile] = {
    ClosableFile.resourceFile(file.toPath).evalTap {
      file =>
        F.sync(new FileOutputStream(file.file)).bracketAuto {
          fos => F.sync(fos.write(content))
        }
    }
  }

  private def resolve(
    artifact: NPMArtifact,
    cache: ConcurrentActionCache[F, NPMArtifact, NPMDescriptor],
  ): F[Throwable, ToDownload] = {
    for {
      descriptor <- cache.retrieve(artifact, a => downloadDescriptor(a))
      version    <- F.fromOption(
        new RuntimeException(s"Version not found: ${artifact.version}")
      )(descriptor.versions.get(artifact.version))
      deps <- F.parTraverse(version.dependencies.getOrElse(Map.empty)) {
        case (artifact, verspec) =>
          for {
            ver <- versionToDownload(verspec)
            out <- resolve(NPMArtifact(artifact, ver), cache)
          } yield {
            out
          }
      }
    } yield {
      ToDownload(
        List(Dist(version.dist, artifact)) ++ deps.flatMap(_.tarballs)
      )
    }
  }

  private def downloadDescriptor(
    artifact: NPMArtifact
  ): F[Throwable, NPMDescriptor] = {
    implicit val d: EntityDecoder[BIOTask, NPMDescriptor] = jsonOf[BIOTask, NPMDescriptor]

    for {
      url <- F.sync(
        Uri.unsafeFromString(
          s"https://registry.npmjs.org/${artifact.name}"
        )
      )
      desc <- client.fetch(Request(Method.GET, url))(
        resp => {
          resp.as[NPMDescriptor]
        }
      )
    } yield desc
  }

  private def versionToDownload(spec: String): F[Throwable, String] = {
    F.pure(if (spec.charAt(0).isDigit) {
      spec
    } else {
      spec.substring(1)
    })
  }
}
