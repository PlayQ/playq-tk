package net.playq.tk.npmresolver

import io.circe.Codec

import java.nio.file.Path

final case class NPMArtifact(name: String, version: String)

final case class NPMDist(shasum: String, tarball: String)
object NPMDist {
  implicit val c: Codec[NPMDist] = io.circe.generic.semiauto.deriveCodec[NPMDist]
}

final case class NPMVersion(dependencies: Option[Map[String, String]], dist: NPMDist)
object NPMVersion {
  implicit val c: Codec[NPMVersion] = io.circe.generic.semiauto.deriveCodec[NPMVersion]
}

final case class NPMDescriptor(versions: Map[String, NPMVersion])
object NPMDescriptor {
  implicit val c: Codec[NPMDescriptor] = io.circe.generic.semiauto.deriveCodec[NPMDescriptor]
}

final case class NPMArtifactResolved(name: String, path: Path, isESM: Boolean)
