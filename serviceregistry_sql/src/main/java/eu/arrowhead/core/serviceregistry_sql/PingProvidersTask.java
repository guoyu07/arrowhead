package eu.arrowhead.core.serviceregistry_sql;


import eu.arrowhead.common.database.ServiceRegistryEntry;
import java.util.Date;
import java.util.List;
import java.util.TimerTask;
import org.apache.log4j.Logger;

public class PingProvidersTask extends TimerTask {

  private static Logger log = Logger.getLogger(PingProvidersTask.class.getName());

  @Override
  public void run() {
    int deleteCount = pingAndRemoveServices();
    log.debug("Removed " + deleteCount + " inactive entries from SR database at " + new Date().toString());
  }

  //Removes Service Registry entries with offline/inactive providers
  private int pingAndRemoveServices() {
    List<ServiceRegistryEntry> srEntries = ServiceRegistryResource.dm.getAll(ServiceRegistryEntry.class, null);
    boolean connectionIsAlive;
    int deleteCount = 0;
    for (ServiceRegistryEntry entry : srEntries) {
      connectionIsAlive = RegistryUtils.pingHost(entry.getProvider().getAddress(), entry.getProvider().getPort(), 10);
      if (!connectionIsAlive) {
        ServiceRegistryResource.dm.delete(entry);
        deleteCount++;
      }
    }
    return deleteCount;
  }

}