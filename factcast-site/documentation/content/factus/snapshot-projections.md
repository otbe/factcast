+++
draft = false
title = "Snapshot Projections"
description = ""


creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe@codesmell.de"


parent = "factus-projections"
identifier = "factus-projections-snapshot"
weight = 7

+++

![](../ph_sp.png)

Now that we know how snapshotting works and what a projection is, it is quite easy to put things together:

A SnapshotProjection is a Projection (read EventHandler) that can be stored into/created from a Snapshot. Lets go back to the example we had before:

```java
/**
*   maintains a map of UserId->UserName
**/
public class UserNames implements SnapshotProjection {

    private final Map<UUID, String> existingNames = new HashMap<>();

    @Handler
    void apply(UserCreated created) {
        existingNames.put(created.aggregateId(), created.userName());
    };

    @Handler
    void apply(UserDeleted deleted) {
        existingNames.remove(deleted.aggregateId());
    };
// ...
``` 

This projection is interested in `UserCreated` and `UserDeleted` EventObjects and can be serialized by the `SnapshotSerializer`.

If you have worked with FactCast before, you'll know what needs to be done (if you haven't, just skip this section and be happy not to be bothered by this anymore):
1. create an instance of the projection class, or get a Snapshot from somewhere
1. create a list of FactSpecs (FactSpecifications) including the Specifications from `UserCreated` and `UserDeleted`
1. create a FactObserver that implements an `void onNext(Fact fact)` method, that
    1. looks at the fact's namespace/type/version
    1. deserializes the payload of the fact into the right EventObject's instance
    1. calls a method to actually process that EventObject
    1. keeps track of facts being successfully processed
1. subscribe to a fact stream according to the FactSpecs from above (either from Scratch or from the last factId processed by the instance from the snapshot)
1. await complete on the subscription to be sure to recieve all EventObjects currently in the EventLog
1. maybe create a snapshot manually and store it somewhere, so that you do not have to start from scratch next time

... and this is just the "happy-path".

With Factus however, all you need to do is to use the following method:

```java
    /**
     * If there is a matching snapshot already, it is deserialized and the
     * matching events, which are not yet applied, will be. Afterwards, a new
     * snapshot is created and stored.
     * <p>
     * If there is no existing snapshot yet, or they are not matching (see
     * serialVersionUID), an initial one will be created.
     *
     * @return an instance of the projectionClass in at least initial state, and
     *         (if there are any) with all currently published facts applied.
     */
    @NonNull
    <P extends SnapshotProjection> P fetch(@NonNull Class<P> projectionClass);
```

like

```java
UserNames currentUserNames = factus.fetch(UserNames.class);
```
Easy uh? As the instance is created from either a Snapshot or the class, the instance is private to the caller here. This is the reason, why there is no ConcurrentHashMap or any other kind of synchronization necessary within `UserNames`.