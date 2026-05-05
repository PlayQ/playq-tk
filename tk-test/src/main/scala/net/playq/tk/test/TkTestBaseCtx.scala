package net.playq.tk.test

import distage.{DefaultModule2, TagKK}
import izumi.distage.constructors.ClassConstructor
import izumi.distage.model.providers.Functoid
import izumi.functional.bio.Functor2

abstract class TkTestBaseCtx[F[+_, +_]: Functor2: TagKK: DefaultModule2, Ctx: ClassConstructor] extends TkTestBaseNoCtx[F] {
  protected final def bound(f: Ctx => F[Any, Any]): Functoid[F[Any, Unit]] = {
    boundWith(identity)(f)
  }

  protected final def boundExt(f: Functoid[Ctx => F[Any, Any]]): Functoid[F[Any, Unit]] = {
    ClassConstructor[Ctx].flatAp(f).map(_.void)
  }

  protected final def boundExtCtx[Ext: ClassConstructor](f: (Ctx, Ext) => F[Any, Any]): Functoid[F[Any, Unit]] = {
    boundWith(_.zip(ClassConstructor[Ext])) { case (ctx, ext) => f(ctx, ext) }
  }

  protected final def boundWith[Ext](mod: Functoid[Ctx] => Functoid[Ext])(f: Ext => F[Any, Any]): Functoid[F[Any, Unit]] = {
    mod(ClassConstructor[Ctx]).map(f(_).void)
  }
}
