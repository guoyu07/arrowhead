package eu.arrowhead.common.messages;

import eu.arrowhead.common.database.OrchestrationStore;
import java.util.ArrayList;
import java.util.List;

public class OrchestrationStoreQueryResponse {

  private List<OrchestrationStore> entryList = new ArrayList<>();

  public OrchestrationStoreQueryResponse() {
  }

  public OrchestrationStoreQueryResponse(List<OrchestrationStore> entryList) {
    this.entryList = entryList;
  }

  public List<OrchestrationStore> getEntryList() {
    return entryList;
  }

  public void setEntryList(List<OrchestrationStore> entryList) {
    this.entryList = entryList;
  }

}
