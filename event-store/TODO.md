My ad-hoc to-do list for this project.

- [ ] Each event has tenant and namespace fields but then, in the ID, these same fields are present.  These fields should be removed from the event and the ID should contain the full qualified identity.
- [ ] Remove the multi-tenant option; currently this is stored in the Config.
- [ ] Add eventStore.storeEvent - there are a number of cases where a single event is wrapped into a list.
- [ ] Make APIs lifecycled using an event stream