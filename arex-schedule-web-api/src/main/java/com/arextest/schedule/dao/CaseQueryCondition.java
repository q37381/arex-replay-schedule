package com.arextest.schedule.dao;

import java.util.List;
import lombok.Data;

/**
 * Storage-agnostic replacement of the mongo Criteria list that used to leak
 * into the service layer via PlanExecutionContext. The two repository
 * editions translate it into Criteria (mongo) or SQL where-clauses (mysql)
 * respectively.
 */
@Data
public class CaseQueryCondition {

  /**
   * Matches ReplayRunDetails.contextIdentifier when set.
   */
  private String contextIdentifier;

  /**
   * Matches rows whose contextIdentifier is null (the "no-config" context).
   * Mutually exclusive with {@link #contextIdentifier}.
   */
  private boolean nullContextIdentifier;

  /**
   * Matches rows whose planItemId is in this list (re-run plans).
   */
  private List<String> planItemIds;
}
