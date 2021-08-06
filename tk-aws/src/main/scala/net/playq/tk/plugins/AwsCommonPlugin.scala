package net.playq.tk.plugins

import izumi.distage.plugins.PluginDef
import net.playq.tk.aws.config.LocalTestCredentials

object AwsCommonPlugin extends PluginDef {
  include(AwsTagsModule)
  make[LocalTestCredentials].from(LocalTestCredentials.default)
}
