# **D4S**  - *"Dynamo DB Database Done Scala-way"*
<p align="center">
<img width="40%" src="./vuepress/docs/.vuepress/public/D4S_logo.svg" alt="Logo"/>
</p>

What is it?  ![Build](https://github.com/PlayQ/playq-tk/workflows/Build/badge.svg)
===========
__*D4S*__ - is a Scala library that allows you to work with DynamoDB in a pure functional way.
It's powered by [Izumi](https://izumi.7mind.io/latest/release/doc/index.html), uses Bifunctor IO and allows you to choose whatever effect type you want to use. It provides flexible and extensible DSL, supports AWS SDK v2 and has great integration with [ZIO](https://zio.dev/).

include the following components:

1. _d4s_ – core package, the lib itself.
2. _d4s-circe_ – provides circe codecs to encode the data.
3. _d4s-test_ - provides test environment and docker containers via DIstage TestKit.

Please proceed to the [microsite](https://playq.github.io/playq-tk/d4s/about) for more information.   