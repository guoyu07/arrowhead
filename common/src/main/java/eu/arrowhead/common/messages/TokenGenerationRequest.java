package eu.arrowhead.common.messages;

import eu.arrowhead.common.database.ArrowheadCloud;
import eu.arrowhead.common.database.ArrowheadService;
import eu.arrowhead.common.database.ArrowheadSystem;
import java.util.ArrayList;
import java.util.List;

public class TokenGenerationRequest {

  private ArrowheadSystem consumer;
  private ArrowheadCloud consumerCloud;
  private List<ArrowheadSystem> providers = new ArrayList<>();
  private ArrowheadService service;
  private int duration;

  public TokenGenerationRequest() {
  }

  public TokenGenerationRequest(ArrowheadSystem consumer, ArrowheadCloud consumerCloud, List<ArrowheadSystem> providers, ArrowheadService service,
                                int duration) {
    this.consumer = consumer;
    this.consumerCloud = consumerCloud;
    this.providers = providers;
    this.service = service;
    this.duration = duration;
  }

  public ArrowheadSystem getConsumer() {
    return consumer;
  }

  public void setConsumer(ArrowheadSystem consumer) {
    this.consumer = consumer;
  }

  public ArrowheadCloud getConsumerCloud() {
    return consumerCloud;
  }

  public void setConsumerCloud(ArrowheadCloud consumerCloud) {
    this.consumerCloud = consumerCloud;
  }

  public List<ArrowheadSystem> getProviders() {
    return providers;
  }

  public void setProviders(List<ArrowheadSystem> providers) {
    this.providers = providers;
  }

  public ArrowheadService getService() {
    return service;
  }

  public void setService(ArrowheadService service) {
    this.service = service;
  }

  public int getDuration() {
    return duration;
  }

  public void setDuration(int duration) {
    this.duration = duration;
  }

}