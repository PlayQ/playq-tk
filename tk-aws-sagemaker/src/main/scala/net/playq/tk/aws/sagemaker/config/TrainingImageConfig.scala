package net.playq.tk.aws.sagemaker.config

import TrainingImageConfig.FrameworkConfig

final case class TrainingImageConfig(
  trainingImages: List[FrameworkConfig]
)

object TrainingImageConfig {

  final case class FrameworkConfig(
    framework: String,
    processors: List[String],
    versions: List[VersionConfig],
  )

  final case class VersionConfig(
    version: String,
    pyVersions: List[String],
    registries: Map[String, String],
    repository: String,
  )

}
