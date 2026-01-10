My ad-hoc to-do list for this project.

- [ ] When configuring a consumer, allow it to set a page size.  If the page size is one, then it'll only deliver a single event at a time.  One at a time delivery does not have an envelope.
- [ ] The tenant ID, namespace ID, and topic ID are actually their names rather than the actual UUID IDs.  This needs to be changed to the actual IDs.
- [ ] Give TenantID, NamespaceID, and TopicID their own types rather than just using strings everywhere.
- [x] Change the internal representation of EventId into its constituent parts.



I have a concern that the event ID is using the tenant name, namespace name, and topic name rather than their respective IDs.  When any of these names change, the events are not locatable - this is a defect.

Please replace the EventId's constituents to be the UUID ID values rather than the names. 