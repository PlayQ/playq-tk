# Table definition

Let's imagine that we want to implement game leaderboard. The leaderboard would contain the user's id and score.
Here is how the possible interface for a leaderboard persistence could look like:
```scala
trait Ladder[F[_, _]] {
  def submitScore(userId: UserId, score: Score): F[QueryFailure, Unit]
  def getScores: F[QueryFailure, List[UserWithScore]]
}

final case class UserId(value: UUID) extends AnyVal
final case class Score(value: Long) extends AnyVal
final case class UserWithScore(userId: UserId, score: Score)
final case class QueryFailure(queryName: String, cause: Throwable)
  extends RuntimeException(
    s"""Query "$queryName" failed with ${cause.getMessage}""",
    cause,
  )
```

To define a table you must extend `DynamoTableDef`. In this particular case, we have a SUPER-simple table without indexes
and with `hash key` only.
```scala
final class LadderTable(implicit meta: DynamoMeta) extends DynamoTableDef {
  override val name: String             = "d4s-ladder-table"
  override val serviceName: ServiceName = ServiceName.Empty
  override val key: DynamoKey[UUID]     = DynamoKey[UUID](hashKey = DynamoField[UUID]("userId"))
  override val ttl: Option[String]      = None
  override val backupEnabled: Boolean   = false

  def mainFullKey(userId: UserId): Map[String, AttributeValue] = {
    mainKey.bind(userId.value)
  }
}
``` 
`DynamoKey` is used to describe the type of the key. `DynamoKey` type has several constructors. We use the one with `hashKey` only, but you could also specify
`rangeKey` value.
You can describe global and local indexes, provide additional attributes and even set up provisioning throughput with `DynamoTableDef` DSL (with following methods: `makeGlobalIndex`, `makeLocalIndex` and `addAdditionalAttributes`).
More information about a table's ddl and indexes you could find [here](indexes.md).
You could notice that we make a helper function **mainFullKey** to access key indirectly, which is not a necessary thing to do.
One thing we forgot to cover is implicit DynamoMeta parameter. It provides AWS namespace and provisioning config.
 
We mustn't forget to define codecs that convert data types we want to store in DB to AWS format. Hopefully, D4S has capabilities to automatically derive codes from user's defined types. This could be made with
`D4SCodec.derived` macros that successfully derives an encoder and decoder instances for the user's defined data type.
```scala
object LadderTable {
  final case class UserIdWithScoreStored(userId: UUID, score: Long){
    def toAPI: UserWithScore = UserWithScore(UserId(userId), Score(score))
  }
  object UserIdWithScoreStored {
    implicit val codec: D4SCodec[UserIdWithScoreStored] = D4SCodec.derived[UserIdWithScoreStored]
  }
}
```
By default, D4S relies on [Magnolia](https://propensive.com/opensource/magnolia/) to derive typeclasses,
but you could also use [Circe](https://circe.github.io/circe/) to do the same. In case you want to use `circe` just
include `d4s-circe` module as a dependency for your project.

Now, we are ready to make some queries!!!
