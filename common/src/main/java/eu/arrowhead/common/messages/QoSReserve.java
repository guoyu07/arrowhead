package eu.arrowhead.common.messages;

import eu.arrowhead.common.database.ArrowheadService;
import eu.arrowhead.common.database.ArrowheadSystem;
import java.util.HashMap;
import java.util.Map;

public class QoSReserve {

  private ArrowheadSystem provider;
  private ArrowheadSystem consumer;
  private ArrowheadService service;
  private Map<String, String> requestedQoS = new HashMap<>();
  private Map<String, String> commands = new HashMap<>();

  public QoSReserve() {
  }

  public QoSReserve(ArrowheadSystem provider, ArrowheadSystem consumer, ArrowheadService service, Map<String, String> requestedQoS,
                    Map<String, String> commands) {
    this.provider = provider;
    this.consumer = consumer;
    this.service = service;
    this.requestedQoS = requestedQoS;
    this.commands = commands;
  }

  public Map<String, String> getRequestedQoS() {
    return requestedQoS;
  }

  public void setRequestedQoS(Map<String, String> requestedQoS) {
    this.requestedQoS = requestedQoS;
  }

  public Map<String, String> getCommands() {
    return commands;
  }

  public void setCommands(Map<String, String> commands) {
    this.commands = commands;
  }

  public ArrowheadSystem getProvider() {
    return provider;
  }

  public void setProvider(ArrowheadSystem provider) {
    this.provider = provider;
  }

  public ArrowheadSystem getConsumer() {
    return consumer;
  }

  public void setConsumer(ArrowheadSystem consumer) {
    this.consumer = consumer;
  }

  public ArrowheadService getService() {
    return service;
  }

  public void setService(ArrowheadService service) {
    this.service = service;
  }

}
