# Indexes

When there is a need to query records based on some field, there are two ways to do it - use full table scan 
with filter condition or query buy index with key or condition which is much more efficient.  
In conditionals example we used full table scan with condition to filter out records which have score higher than 100. 
Now lets created a global index for score field in our LadderTable by adding following code to our table definition
```scala
val scoreKey: Dynamo[String, Nothing] = DynamoKey[String]("score")
val indexByScore: GlobalIndex[String, Nothing] = makeGlobalIndex[String, Nothing]("score-index", scoreKey)
```
This will create index on score key which represents score field with name *score-index*.
Now we can change **higherThan** method from conditionals example to use index
```scala
def higherThan(score: Long): F[QueryFailure, List[UserWithScore]] = {
  table
    .query(table.scoreIndex)
    .withFilterExpression("score".of[Long] > 100)
    .decodeItems[UserIdWithScoreStored]
    .leftMap(err => QueryFailure(err.message, err.cause))
    .map(_.map(_.toAPI))
}
```
D4S also supports creating local indexes with **makeLocalIndex**
Both indexes can be created only with specific fields using projection
```scala
makeGlobalIndex[String, Nothing](
  "score-index",
  scoreKey,
  projection = Projection
    .builder()
    .projectionType(ProjectionType.INCLUDE)
    .nonKeyAttributes("score").build()
)
```