package rs.raf.simpledb.planner;

import rs.raf.simpledb.tx.Transaction;
import rs.raf.simpledb.query.*;
import rs.raf.simpledb.parse.*;
import rs.raf.simpledb.SimpleDBEngine;
import java.util.*;

/**
 * The simplest, most naive query planner possible.
 * @author Edward Sciore
 */
public class BasicQueryPlanner implements QueryPlanner {
   
   /**
    * Creates a query plan as follows.  It first takes
    * the product of all tables and views; it then selects on the predicate;
    * and finally it projects on the field list. 
    */
   public Plan createPlan(QueryData data, Transaction tx) {
      //Step 1: Create a plan for each mentioned table or view
      List<Plan> plans = new ArrayList<Plan>();
      for (String tblname : data.tables()) {
         String viewdef = SimpleDBEngine.catalogMgr().getViewDef(tblname, tx);
         if (viewdef != null)
            plans.add(SimpleDBEngine.planner().createQueryPlan(viewdef, tx));
         else
            plans.add(new TablePlan(tblname, tx));
      }
      
      //Step 2: Create the product of all table plans
      Plan p = plans.remove(0);
      for (Plan nextplan : plans)
         p = new CrossProductPlan(p, nextplan);
      
      //Step 3: Add a selection plan for the predicate
      p = new SelectionPlan(p, data.pred());
      
      //Step 4: Project on the field names
      p = new ProjectionPlan(p, data.fields());
      return p;
   }
}
