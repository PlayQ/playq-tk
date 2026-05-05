# Conditionals
D4S supports typed conditional expressions which can be used to filter out query or scan results.
Sometimes we want to fetch all users with score above some value from out ladder repository to grant them a gift.
```scala
def higherThan(score: Long): F[QueryFailure, List[UserWithScore]] = {
  table
    .scan
    .withFilterExpression("score".of[Long] > 100)
    .decodeItems[UserIdWithScoreStored]
    .leftMap(err => QueryFailure(err.message, err.cause))
    .map(_.map(_.toAPI))
}
```
Here we filter out all records which have score lower than 100. Few operations can be combined to meet more 
sophisticated filtering conditions. For example, for some reason we need users whose id starts with string "abc"
and whose score is higher than 100, this could be achieved by: 
```scala
  table
    .scan
    .withFilterExpression("score".of[Long] > 100 AND (userId.of[String] beginsWith "abc"))
  ...
```

Conditional expressions (e.g "score".of[Long] > 100) cam be combined with:
* AND/&& 
* OR/||  
or negated with: 
* unary_!

The fields can be compared to values with following expressions:
```
    >, >=, <, <=, === (equals), <> (not equals), betweeen 
```
Any field in a record may be checked against some condition: 
```
isNull/notNull        - field is null ? 
existsField/notExists - field exists ? 
hasType               - field has type of ?   
```
For example, if we want to fetch only those records from table which have field name of string type  
```scala
table
    .scan
    .withFilterExpression("name".existsField AND "name".hasType("S"))
```


