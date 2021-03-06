package eu.arrowhead.common.messages;

import eu.arrowhead.common.database.ArrowheadCloud;
import eu.arrowhead.common.database.ArrowheadService;

public class GSDPoll {

  private ArrowheadService requestedService;
  private ArrowheadCloud requesterCloud;

  public GSDPoll() {
  }

  public GSDPoll(ArrowheadService requestedService, ArrowheadCloud requesterCloud) {
    this.requestedService = requestedService;
    this.requesterCloud = requesterCloud;
  }

  public ArrowheadService getRequestedService() {
    return requestedService;
  }

  public void setRequestedService(ArrowheadService requestedService) {
    this.requestedService = requestedService;
  }

  public ArrowheadCloud getRequesterCloud() {
    return requesterCloud;
  }

  public void setRequesterCloud(ArrowheadCloud requesterCloud) {
    this.requesterCloud = requesterCloud;
  }

  public boolean isValid() {
    return requestedService != null && requestedService.isValid() && requesterCloud != null && requesterCloud.isValidForDatabase();
  }

}
