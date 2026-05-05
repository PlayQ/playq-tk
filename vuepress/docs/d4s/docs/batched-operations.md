# Batched operations

Let's say we want to add method **submitScores** to our Ladder repository:
```scala
def submitScores(ids: List[(UserId, Score)]): F[QueryFailure, Unit]
```
One possible way to do this is to traverse the list and use **table.putItem** to add every pair separately, however this 
approach is not efficient at best. The better way to do this is to use batch operation:
```scala
table.putItemBatch(ids.map { case (user, score) => UserIdWithScore(user, score) }.void)
```

There are other batch operation which D4S provides: 
```scala
table.getItemBatch ...
table.deleteItemBatch ...
table.queryDeleteBatch ...
table.scanDeleteBatch ... 
```
query- and scan- delete  batch operations will at first download all the queried/scanned items and then delete them 
in parallel.  

The default size of the batch is 25 (which DynamoDB restriction) which can be set in config by editing **writeBatchSize**
value. In case there are more than 25 items in a list, a few requests will be sent until all items are processed.

D4S also handles unprocessed items sending additional request to re-process after delay
which by default is set to 0 but can be set in config by editing value **unprocessedBatchSleep**


