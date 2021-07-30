package net.playq.tk.aws.sagemaker.model

import izumi.fundamentals.collections.nonempty.NonEmptyList
import TrainingScript.SourceFile

final case class TrainingScript(
  sourceFiles: NonEmptyList[SourceFile],
  framework: String,
  frameworkVersion: String,
  pythonVersion: String,
)

object TrainingScript {
  final case class SourceFile(filename: String, sourceCode: String)
}
