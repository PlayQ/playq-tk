package net.playq.tk.plugins

import izumi.distage.model.definition.ModuleDef
import izumi.distage.plugins.PluginDef
import izumi.functional.bio.*
import izumi.fundamentals.platform.functional.Identity2
import izumi.logstage.api.IzLogger
import logstage.*
import net.playq.tk.concurrent.FiberWatchdog
import zio.IO

object TkImplicitsPlugin extends PluginDef with IO2ImplicitsModule with ZIORuntimeModule

private[plugins] trait IO2ImplicitsModule extends ModuleDef {
  make[LogIO2[IO]]
    .from(LogZIO.withFiberId(_: IzLogger))
    .aliased[UnsafeLogIO2[IO]]
    .aliased[LogCreateIO2[IO]]
  make[LogIO[IO[Throwable, _]]].from((_: LogIO2[IO]).widen[IO[Throwable, _]])

  make[FiberWatchdog[IO]].from[FiberWatchdog.Impl]

  {
    import izumi.functional.bio.Root.BIOIdentity2
    addImplicit[Applicative2[Identity2]]
  }
}
