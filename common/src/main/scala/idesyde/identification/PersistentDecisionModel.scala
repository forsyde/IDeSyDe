package idesyde.identification


/**
 This [[PersistentDecisionModel]] has information that can be saved in the [[entity ForSyDeSystemGraph]]
 after it has been identified. The main purpose is to enable easier
 exchange partial information between tools, objects or any other entity. 
 
 Supporting a tool-flow where the 'compiler' is invoked multiple times, each
 persisting a new decision model is _not_ part of this trait's goals.

 Checking if the information that should be persisted is consistent and can
 be skipped is part of the internal definition of the [[PersistentDecisionModel]].
 No guarantees of better performance through caching are made by this trait definition.
 That is, this trait dictates the production of persistent information, not
 its administration.
 */
trait PersistentDecisionModel extends DecisionModel:

    /** Build the persistent information from this decision model. */
    def buildPersistence(): Unit

end PersistentDecisionModel
