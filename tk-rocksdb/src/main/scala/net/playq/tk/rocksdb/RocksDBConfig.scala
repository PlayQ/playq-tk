package net.playq.tk.rocksdb

import org.rocksdb.Options

final case class RocksDBConfig(
  createIfMissing: Boolean,
  writeBufferSize: Long,
  paranoidChecks: Boolean,
  paranoidFileChecks: Boolean,
  enablePipelinedWrite: Boolean,
  maxWriteBufferNumber: Int,
  minWriteBufferNumberToMerge: Int,
  maxBackgroundJobs: Int,
) {
  def getOptions(): Options = {
    val opt = new Options()
    opt.setCreateIfMissing(createIfMissing)
    opt.setWriteBufferSize(writeBufferSize)
    opt.setParanoidChecks(paranoidChecks)
    opt.setParanoidFileChecks(paranoidFileChecks)
    opt.setEnablePipelinedWrite(enablePipelinedWrite)
    opt.setMaxWriteBufferNumber(maxWriteBufferNumber)
    opt.setMinWriteBufferNumberToMerge(minWriteBufferNumberToMerge)
    opt.setMaxBackgroundJobs(maxBackgroundJobs)
    opt
  }
}
