package net.playq.tk.test

import distage.{DefaultModule2, TagKK}
import izumi.distage.constructors.AnyConstructor
import izumi.distage.model.providers.Functoid
import izumi.functional.bio.Applicative2

abstract class TkTestBaseCtx[F[+_, +_]: Applicative2: TagKK: DefaultModule2, Ctx: AnyConstructor] extends TkTestAbstract[F] {
  protected final def scopeIO(f: Ctx => F[?, ?]): Functoid[F[?, Unit]] = AnyConstructor[Ctx].map(f(_).void)
}
