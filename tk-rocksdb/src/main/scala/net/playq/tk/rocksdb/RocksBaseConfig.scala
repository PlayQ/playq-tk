package net.playq.tk.rocksdb

import distage.Lifecycle
import izumi.functional.bio.{F, IO2}
import org.rocksdb.Options

import scala.concurrent.duration.FiniteDuration

final case class RocksBaseConfig(
  createIfMissing: Boolean,
  writeBufferSize: Long,
  paranoidChecks: Boolean,
  paranoidFileChecks: Boolean,
  enablePipelinedWrite: Boolean,
  maxWriteBufferNumber: Int,
  minWriteBufferNumberToMerge: Int,
  maxBackgroundJobs: Int,
  maxOpenFiles: Int,
  flushInterval: FiniteDuration,
  defaultBaseDir: String,
) {
  def makeOptions[F[+_, +_]: IO2]: Lifecycle[F[Throwable, _], Options] = {
    Lifecycle.make {
      F.sync {
        new Options()
          .setCreateIfMissing(createIfMissing)
          .setWriteBufferSize(writeBufferSize)
          .setParanoidChecks(paranoidChecks)
          .setParanoidFileChecks(paranoidFileChecks)
          .setEnablePipelinedWrite(enablePipelinedWrite)
          .setMaxWriteBufferNumber(maxWriteBufferNumber)
          .setMinWriteBufferNumberToMerge(minWriteBufferNumberToMerge)
          .setMaxBackgroundJobs(maxBackgroundJobs)
          .setMaxOpenFiles(maxOpenFiles)
      }
    }(opt => F.sync(opt.close()))
  }
}
