package net.playq.tk.aws.sagemaker

import izumi.functional.bio.{ApplicativeError2, F}
import net.playq.tk.aws.sagemaker.TrainingImageProvider.{GettingTrainingImageURIException, TrainingImageURI}
import net.playq.tk.aws.sagemaker.config.{SagemakerConfig, TrainingImageConfig}
import net.playq.tk.aws.sagemaker.model.{TrainingHardware, TrainingScript}

final class TrainingImageProvider[F[+_, +_]: ApplicativeError2](
  config: SagemakerConfig,
  trainingImageConfig: TrainingImageConfig,
) {

  def trainingImageUri(trainingScript: TrainingScript, trainingHardware: TrainingHardware): F[GettingTrainingImageURIException, TrainingImageURI] = {
    F.fromEither {
      getTrainingImageURI(
        config.region,
        trainingHardware.hardwareType.qualifier,
        trainingScript.framework,
        trainingScript.frameworkVersion,
        trainingScript.pythonVersion,
      )
    }.bimap(GettingTrainingImageURIException, TrainingImageURI)
  }

  private[this] def getTrainingImageURI(
    region: String,
    hardwareType: String,
    framework: String,
    frameworkVersion: String,
    pythonVersion: String,
  ): Either[String, String] = {
    for {
      frameworkConf <- trainingImageConfig.trainingImages.find(_.framework == framework).toRight(s"No training image configuration found for framework=$framework")
      _ <-
        if (frameworkConf.processors.contains(hardwareType)) Right(())
        else Left(s"Hardware type `$hardwareType` is not supported by framework=$framework, supported hardware types: ${frameworkConf.processors.mkString(",")}")

      versionConfig <- frameworkConf.versions.find(_.version == frameworkVersion).toRight {
        s"No training image configuration found for version=$frameworkVersion of framework=$framework, available versions: ${frameworkConf.versions.map(_.version).mkString(",")}"
      }
      _ <-
        if (versionConfig.pyVersions.contains(pythonVersion)) Right(())
        else
          Left(
            s"Python version `$pythonVersion` is not supported by version=$frameworkVersion of framework=$framework, available versions: ${versionConfig.pyVersions.mkString(",")}"
          )
      registry <- versionConfig.registries.get(region).toRight {
        s"No training image configuration found for region=$region of version=$frameworkVersion of framework=$framework, available regions: ${versionConfig.registries.keys
          .mkString(",")}"
      }

      repository = versionConfig.repository
      image      = s"$repository:$frameworkVersion-$hardwareType-$pythonVersion"
      res        = s"$registry.dkr.ecr.$region.amazonaws.com/$image"
    } yield res

  }

}

object TrainingImageProvider {
  final case class TrainingImageURI(uri: String) extends AnyVal
  final case class GettingTrainingImageURIException(reason: String) extends RuntimeException(reason)
}
