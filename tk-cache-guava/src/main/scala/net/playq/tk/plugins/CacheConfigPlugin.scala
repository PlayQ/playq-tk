package net.playq.tk.plugins

import distage.config.ConfigModuleDef
import distage.plugins.PluginDef
import net.playq.tk.cache.CacheConfig

object CacheConfigPlugin extends PluginDef with ConfigModuleDef {
  makeConfigNamed[CacheConfig]("cache")
}
