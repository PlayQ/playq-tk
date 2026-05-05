package net.playq.tk.aws.sagemaker.model

import izumi.fundamentals.collections.nonempty.NEList
import net.playq.tk.aws.sagemaker.model.TrainingScript.SourceFile

final case class TrainingScript(
  sourceFiles: NEList[SourceFile],
  framework: String,
  frameworkVersion: String,
  pythonVersion: String,
)

object TrainingScript {
  final case class SourceFile(filename: String, sourceCode: String)
}
