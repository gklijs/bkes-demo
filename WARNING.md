# Not production ready for several reasons

* `nl.openweb.topology.clients/consume-all-from-start` does not wait till ready, which can cause inconsistencies. This
  can be improved by checking the end offsets before using `consumer.seekToEnd()` and return only once these are
  reached.
* Nothing is implemented to prevent two concurrent modifications on the same aggregate. Ideally you work with version
  numbers, and the first to hit the 'server' is accepted.
* Both the Command Handler and the Projector use a in memory db, that just keeps growing and at some point causes them
  to crash. You still want to keep something in memory. For the Command handler you could keep the recent aggregates,
  and retrieve them via bkes when needed. For the Projector you could use something
  like [crux](https://github.com/juxt/crux). Crux has the added advantage of making queries related to time easy, and we
  already have the event times.
* A saga should end within the timeout, currently 1000ms, and on the same instance it was started. They could end in a
  sort of limbo and not ever complete. They might also need to take longer than that. It's possible to leverage Kafka to
  manage which saga is handled by which instance. It's not easy to solve however, because some event crucial for the
  saga processing might have passed while the assignment of the saga is changed. There is no easy way to properly
  rebuild the saga state, although all relevant events use the same key.
* A command message or query message might be lost if it's not consumed right after it's put on Kafka. This might be
  leveraged by directly passing the message to the correct instances. This might be done by adding a server, it would be
  a kind of proxy when services can register itself, and the server is just passing the message to on of the applicable
  services, or all in case of some queries. It might also allow for streaming queries.
* There is just one Kafka broker, and it hasn't setup to be secure. Ideally we have 3 brokers at minimal to prevent
  losing data. The brokers should be secured, for example using ssl certificates. Kafka also supports settings acls, so
  for example only the command-handler is allowed to produce to the event topics.
* When restarting the Projector, it will send all the transactions again. This can be prevented by either checking the
  time of the events, and not sending the 'derived' events when it's already much later. Another way to handle this, is
  to think of these as subscriptions, much like the GraphQL subscriptions. So instead of just putting it all on Kafka,
  it needs a client to subscribe to updates. This is the way it's done in Axon using an QueryUpdateEmitter, see
  the [example](https://github.com/gklijs/bank-axon-graphql/blob/e59b83f9d9ed0f133495ba8d92fcd327542ff0bc/projector/src/main/kotlin/nl/openweb/projector/TransactionProjector.kt#L44)
  from the similar project.