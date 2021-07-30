package net.playq.tk.aws.sagemaker.model

import TrainingHardware.{TrainingHardwareType, instanceTypes}
import TrainingHardware.TrainingHardwareType.CPU

final case class TrainingHardware(
  instanceType: String,
  instanceCount: Int,
  hardwareType: TrainingHardwareType,
  volumeSizeInGB: Int,
) {
  require(instanceTypes contains instanceType, s"`$instanceType` must be in $instanceTypes")
}

object TrainingHardware {
  def default: TrainingHardware = TrainingHardware(
    instanceType   = "ml.c5.xlarge",
    instanceCount  = 1,
    hardwareType   = CPU,
    volumeSizeInGB = 1,
  )

  sealed abstract class TrainingHardwareType(val qualifier: String)
  object TrainingHardwareType {
    case object CPU extends TrainingHardwareType("cpu")
    case object GPU extends TrainingHardwareType("gpu")
    case object Inferentia extends TrainingHardwareType("inf")
  }

  val instanceTypes = Set(
    "ml.p2.xlarge",
    "ml.m5.4xlarge",
    "ml.m4.16xlarge",
    "ml.p4d.24xlarge",
    "ml.c5n.xlarge",
    "ml.p3.16xlarge",
    "ml.m5.large",
    "ml.p2.16xlarge",
    "ml.c4.2xlarge",
    "ml.c5.2xlarge",
    "ml.c4.4xlarge",
    "ml.c5.4xlarge",
    "ml.c5n.18xlarge",
    "ml.g4dn.xlarge",
    "ml.g4dn.12xlarge",
    "ml.c4.8xlarge",
    "ml.g4dn.2xlarge",
    "ml.c5.9xlarge",
    "ml.g4dn.4xlarge",
    "ml.c5.xlarge",
    "ml.g4dn.16xlarge",
    "ml.c4.xlarge",
    "ml.g4dn.8xlarge",
    "ml.c5n.2xlarge",
    "ml.c5n.4xlarge",
    "ml.c5.18xlarge",
    "ml.p3dn.24xlarge",
    "ml.p3.2xlarge",
    "ml.m5.xlarge",
    "ml.m4.10xlarge",
    "ml.c5n.9xlarge",
    "ml.m5.12xlarge",
    "ml.m4.xlarge",
    "ml.m5.24xlarge",
    "ml.m4.2xlarge",
    "ml.p2.8xlarge",
    "ml.m5.2xlarge",
    "ml.p3.8xlarge",
    "ml.m4.4xlarge",
  )
}
