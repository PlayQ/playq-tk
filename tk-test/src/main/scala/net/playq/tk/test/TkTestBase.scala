package net.playq.tk.test

import izumi.distage.constructors.ClassConstructor
import zio.IO

abstract class TkTestBase[Ctx: ClassConstructor] extends TkTestBaseCtx[IO, Ctx]
