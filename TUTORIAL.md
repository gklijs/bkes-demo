# Tutorial

For the tutorial we first need to get the project running locally.
Once that's done, and you are a bit familiar with the set up, you gan pick on of the suggestion of expanding the project.

## Running the project

Because it's based on a couple of components, and we are using Kafka as backend, the combined memory resources we need for Docker are about 4 GB.
When you have a total of 8 GB it should be possible to develop and run the project at the same time.
Please make sure you have the [prerequisites](README.md#prerequisites) installed needed to run the project.
The easiest way to get everything running is using docker, as described [here](README.md#using-docker).
Once you are able to see the frontend make sure to login, do a couple of transactions and switch to the employee to be sure it's working. In the [readme](README.md) you can find explanations of all the components.

## Suggestions for expanding the project

These are a couple of suggestions of what can be done to expand the project. The first ones are more focussed on frontend development, and the last one on backend development. The suggestions are:
* [Add multiple bank accounts](#multiple)
* [Add shared accounts](#shared)
* [Build the projector using crux](#crux)

### <a id="multiple">Add multiple bank accounts</a>

The same command, `CreateBankAccountCommand` which is used to add the initial bank account can also be used to create additional bank accounts. We need a couple of changes to support this feature:
* In the [schema](graphql-endpoint/resources/graphql-schema.edn) we need to add a mutation to add bank account.
* In the same schema, we need to add the `AccountResult` type, adding a list of iban and token pairs for all the bank accounts.
* A resolver function need to be added to the [account creation service](graphql-endpoint/src/nl/openweb/graphql_endpoint/account_creation_service.clj), matching the added mutation, which needs to be hooked on to the schema in [schema.cls](graphql-endpoint/src/nl/openweb/graphql_endpoint/schema.clj).
  Also in the account creation server the `get-account` function needed to be changed to also supply the list of iban and tokens. The response from the projector should already contain all the bank accounts.
* With these changes in place we can change the [events.cljs](frontend/src/cljs/nl/openweb/bank/events.cljs), by adding the iban and token pairs to the query, and storing them in the db.
* The final changes to the frontend are adding a button to add a new account, and have a switch to change the active bank account.

### <a id="shared">Add shared accounts</a>

The `AddUserToBankAccountCommand` can be used to add a user to an existing bank account. Please note we also need the token to successfully perform this action.
As the username is not checked, we can add a user to a bank account before the user exists, so we don't need to take into account multiple bank accounts.
This can be used to create shared bank accounts, where both users are able to do transactions with the account. Some changes are needed to support this functionality.
The changes needed are:
* In the [schema](graphql-endpoint/resources/graphql-schema.edn) we need to add a mutation to add a user to the bank account.
* A resolver function need to be added to the [account creation service](graphql-endpoint/src/nl/openweb/graphql_endpoint/account_creation_service.clj), matching the added mutation, which needs to be hooked on to the schema in [schema.cls](graphql-endpoint/src/nl/openweb/graphql_endpoint/schema.clj).
    Also in the account creation server the `get-account` function needed to be changed to check for existing bank accounts, after creating the new user.
* With these changes in place we can change the [events.cljs](frontend/src/cljs/nl/openweb/bank/events.cljs), by adding a `re-frame/reg-event-fx` to register the add user event.
* The final changes to the frontend are adding an input field for the username with a button to add that user to the account, and handle the feedback properly, by showing the error for example. This can be tested by trying to add the current user, which should lead to a `user to add is already owner of bank account` reason for failure.

### <a id="crux">Build the projector using crux</a>

Because we are using Kafka for the events, we get the time the events took place for free. Currently, we are not storing dates in the projection, so it's also not possible to query them. Instead of further building on the db namespace in the projector, we could leverage crux, and use datalog for the queries.
This would allow for queries like all the users at a certain moment in time, or the value of the balance for a bank account at a certain point in time. To get there we need:
* Add the [crux](https://github.com/juxt/crux#quick-links) dependency to the projector project.
* Rewrite the code to use crux, so in the [db.clj](projector/src/nl/openweb/projector/db.clj) start with [creating a crux node](https://opencrux.com/reference/21.01-1.14.0/installation.html#embedded). [Transaction functions](https://juxt.pro/blog/crux-dev-diary-june-2020#_transaction_functions) can be used for most events updating a state. `(.timestamp record)` can be used to get the timestamp from the Kafka ConsumerRecord containing the event message.
* Use the frontend or [Graph*i*QL](http://localhost:8888/ide) to check if it still works.
* Add a query to the message types, to use date based queries, like the balance of the account at a certain point in time.

Alternatively we could add this as just another projection, which can be made to work by making sure the queries are routed to the correct projection.

