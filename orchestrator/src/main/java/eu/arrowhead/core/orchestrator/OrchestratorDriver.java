package eu.arrowhead.core.orchestrator;

import eu.arrowhead.common.Utility;
import eu.arrowhead.common.database.ArrowheadCloud;
import eu.arrowhead.common.database.ArrowheadService;
import eu.arrowhead.common.database.ArrowheadSystem;
import eu.arrowhead.common.database.OrchestrationStore;
import eu.arrowhead.common.database.ServiceRegistryEntry;
import eu.arrowhead.common.exception.DataNotFoundException;
import eu.arrowhead.common.messages.GSDAnswer;
import eu.arrowhead.common.messages.GSDRequestForm;
import eu.arrowhead.common.messages.GSDResult;
import eu.arrowhead.common.messages.ICNRequestForm;
import eu.arrowhead.common.messages.ICNResult;
import eu.arrowhead.common.messages.IntraCloudAuthRequest;
import eu.arrowhead.common.messages.IntraCloudAuthResponse;
import eu.arrowhead.common.messages.OrchestrationResponse;
import eu.arrowhead.common.messages.PreferredProvider;
import eu.arrowhead.common.messages.ServiceQueryForm;
import eu.arrowhead.common.messages.ServiceQueryResult;
import eu.arrowhead.common.messages.ServiceRequestForm;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Contains miscellaneous helper functions for the Orchestration process. The main functions of the Orchestration process used by the REST resource
 * are in {@link eu.arrowhead.core.orchestrator.OrchestratorService}.
 *
 * @author Umlauf Zoltán
 */
final class OrchestratorDriver {

  private static Logger log = Logger.getLogger(OrchestratorService.class.getName());

  private OrchestratorDriver() throws AssertionError {
    throw new AssertionError("OrchestratorDriver is a non-instantiable class");
  }

  /**
   * Queries the Service Registry Core System for a specific <tt>ArrowheadService</tt>.
   *
   * @param service The <tt>ArrowheadService</tt> object for which the list of potential <tt>ArrowheadSystem</tt> providers are needed
   * @param metadataSearch If true, the stored <tt>ArrowheadService</tt>s have to have the same metadata to be returned in the response.
   * @param pingProviders If true, the Service Registry is asked to ping the service provider <tt>ArrowheadSystem</tt> (where the service is
   *     offered) to check if a connection can be established or not. Normally providers have to remove their offered services from the Service
   *     Registry before going offline, but this feature can be used to ensure offline providers are filtered out.
   *
   * @return list of potential service providers with their offered services (interfaces, metadata, service URI)
   *
   * @throws DataNotFoundException if the Service Registry response list is empty
   */
  static List<ServiceRegistryEntry> queryServiceRegistry(ArrowheadService service, boolean metadataSearch, boolean pingProviders) {
    // Compiling the URI and the request payload
    String srUri = UriBuilder.fromPath(Utility.getServiceRegistryUri()).path("query").toString();
    ServiceQueryForm queryForm = new ServiceQueryForm(service, pingProviders, metadataSearch);

    // Sending the request, parsing the returned result
    Response srResponse = Utility.sendRequest(srUri, "PUT", queryForm);
    ServiceQueryResult serviceQueryResult = srResponse.readEntity(ServiceQueryResult.class);
    if (serviceQueryResult == null || !serviceQueryResult.isValid()) {
      log.error("queryServiceRegistry DataNotFoundException");
      throw new DataNotFoundException("ServiceRegistry query came back empty for " + service.toString());
    }

    // If there are non-valid entries in the Service Registry response, we filter those out
    List<ServiceRegistryEntry> temp = new ArrayList<>();
    for (ServiceRegistryEntry entry : serviceQueryResult.getServiceQueryData()) {
      if (!entry.isValid()) {
        temp.add(entry);
      }
    }
    serviceQueryResult.getServiceQueryData().removeAll(temp);

    log.info("queryServiceRegistry was successful, number of potential providers for" + service.toString() + " is " + serviceQueryResult
        .getServiceQueryData().size());
    return serviceQueryResult.getServiceQueryData();
  }

  /**
   * Queries the Authorization Core System to see which provider <tt>ArrowheadSystem</tt>s are authorized to offer their services to the consumer.
   *
   * @param consumer The <tt>ArrowheadSystem</tt> object representing the consumer system
   * @param service The <tt>ArrowheadService</tt> object representing the service to be consumed
   * @param providerSet The set of <tt>ArrowheadSystem</tt> objects representing the potential provider systems
   *
   * @return list of the authorized provider <tt>ArrowheadSystem</tt>s
   *
   * @throws DataNotFoundException if none of the provider <tt>ArrowheadSystem</tt>s are authorized for this servicing
   */
  static Set<ArrowheadSystem> queryAuthorization(ArrowheadSystem consumer, ArrowheadService service, Set<ArrowheadSystem> providerSet) {
    // Compiling the URI and the request payload
    String uri = UriBuilder.fromPath(Utility.getAuthorizationUri()).path("intracloud").toString();
    IntraCloudAuthRequest request = new IntraCloudAuthRequest(consumer, providerSet, service);

    // Sending the request, parsing the returned result
    Response response = Utility.sendRequest(uri, "PUT", request);
    IntraCloudAuthResponse authResponse = response.readEntity(IntraCloudAuthResponse.class);
    Set<ArrowheadSystem> authorizedSystems = new HashSet<>();
    // Set view of HashMap ensures there are no duplicates between the keys (systems)
    for (Map.Entry<ArrowheadSystem, Boolean> entry : authResponse.getAuthorizationMap().entrySet()) {
      if (entry.getValue()) {
        authorizedSystems.add(entry.getKey());
      }
    }

    // Throwing exception if none of the providers are authorized for this consumer/service pair.
    if (authorizedSystems.isEmpty()) {
      log.error("queryAuthorization DataNotFoundException");
      throw new DataNotFoundException("The consumer system is not authorized to receive servicing from any of the provider systems.");
    }

    log.info("queryAuthorization is done, sending back " + authorizedSystems.size() + " authorized Systems");
    return authorizedSystems;
  }

  /**
   * Filters out all the entries of the given <tt>ServiceRegistryEntry</tt> list, which does not contain a preferred local <tt>ArrowheadSystem</tt>.
   * This method is called when the <i>onlyPreferred</i> orchestration flag is set to true.
   *
   * @param srList The list of <tt>ServiceRegistryEntry</tt>s still being considered (after SR query and possibly Auth query)
   * @param preferredLocalProviders A set of local <tt>ArrowheadSystem</tt>s preferred by the requester <tt>ArrowheadSystem</tt>. This is a subset
   *     of the <i>preferredProviders</i> list from the {@link ServiceRequestForm}, which can also contain not
   *     local <tt>ArrowheadSystem</tt>s.
   *
   * @return a list of <tt>ServiceRegistryEntry</tt>s which have preferred provider <tt>ArrowheadSystem</tt>s
   *
   * @throws DataNotFoundException if none of the <tt>ServiceRegistryEntry</tt>s from the given list contain a preferred <tt>ArrowheadSystem</tt>
   */
  static List<ServiceRegistryEntry> removeNonPreferred(List<ServiceRegistryEntry> srList, Set<ArrowheadSystem> preferredLocalProviders) {
    // Using a simple nested for-loop for the filtering
    List<ServiceRegistryEntry> preferredList = new ArrayList<>();
    for (ArrowheadSystem system : preferredLocalProviders) {
      for (ServiceRegistryEntry entry : srList) {
        if (system.equals(entry.getProvider())) {
          preferredList.add(entry);
        }
      }
    }

    if (preferredList.isEmpty()) {
      log.error("removeNonPreferred DataNotFoundException");
      throw new DataNotFoundException("No preferred local System was found in the the list of potential provider Systems.");
    }

    log.info("removeNonPreferred returns with " + preferredList.size() + " ServiceRegistryEntries.");
    return preferredList;
  }

  static List<ServiceRegistryEntry> doQoSVerification(List<ServiceRegistryEntry> srList) {
    //placeholder for actual implementation
    return srList;
  }

  /**
   * As the last step of the local orchestration process (if requested with the <i>matchmaking</i> orchestration flag) we pick out 1 provider from the
   * remaining list. Providers preferred by the consumer have higher priority. Custom matchmaking algorithm can be implemented here, as of now it just
   * returns the first (preferred) provider from the list.
   * <p>
   * If the <i>onlyPreferred</i> orchestration flag is set to true, then it is guaranteed there will be at least 1 preferred provider to choose from,
   * since this method is called after {@link #removeNonPreferred(List, Set)}, where a {@link eu.arrowhead.common.exception.DataNotFoundException} is
   * thrown if no preferred provider was found.
   *
   * @param srList The list of <tt>ServiceRegistryEntry</tt>s still being considered
   * @param preferredLocalProviders The set of <tt>ArrowheadSystem</tt>s in this Local Cloud preferred by the requester system
   *
   * @return the chosen ServiceRegistryEntry object, containing the necessary <tt>ArrowheadSystem</tt> and <tt>String</tt> serviceUri information to
   *     contact the provider
   */
  static ServiceRegistryEntry intraCloudMatchmaking(List<ServiceRegistryEntry> srList, Set<ArrowheadSystem> preferredLocalProviders) {
    // If there are no preferred providers, just return the first ServiceRegistryEntry
    if (preferredLocalProviders.isEmpty()) {
      log.info("intraCloudMatchmaking: no preferred local providers given, returning first ServiceRegistryEntry");
      return srList.get(0);
    } else { // Otherwise try to find a preferred provider first
      for (ArrowheadSystem system : preferredLocalProviders) {
        for (ServiceRegistryEntry entry : srList) {
          if (system.equals(entry.getProvider())) {
            log.info("intraCloudMatchmaking: returning the first ServiceRegistryEntry found with preferred provider");
            return entry;
          }
        }
      }
      log.info("intraCloudMatchmaking: no match was found between preferred providers, returning the first ServiceRegistryEntry");
      // And only return the first ServiceRegistryEntry, when no preferred provider was found
      return srList.get(0);
    }
  }

  static List<ServiceRegistryEntry> doQosReservation(List<ServiceRegistryEntry> srList) {
    //placeholder for actual implementation
    return srList;
  }

  /**
   * Queries the Orchestration Store database table for a consumer <tt>ArrowheadSystem</tt>. The Orchestration Store holds <i>hardwired</i>
   * <tt>ArrowheadService</tt>s between consumer and provider <tt>ArrowheadSystem</tt>s. The provider system can be local or part of another cloud.
   * For more information see {@link eu.arrowhead.common.database.OrchestrationStore}.
   *
   * @param consumer The <tt>ArrowheadSystem</tt> object representing the consumer system (mandatory)
   * @param service The <tt>ArrowheadService</tt> object representing the service to be consumed (optional)
   *
   * @return a list of <tt>OrchestrationStore</tt> objects matching the query criteria
   *
   * @throws DataNotFoundException if the Store query yielded no results
   */
  static List<OrchestrationStore> queryOrchestrationStore(@NotNull ArrowheadSystem consumer, @Nullable ArrowheadService service) {
    List<OrchestrationStore> retrievedList;

    //If the service is null, we return all the default store entries.
    if (service == null) {
      retrievedList = StoreService.getDefaultStoreEntries(consumer);
    }
    //If not, we return all the Orchestration Store entries specified by the consumer and the service.
    else {
      retrievedList = StoreService.getStoreEntries(consumer, service);
    }

    if (retrievedList.isEmpty()) {
      log.error("queryOrchestrationStore DataNotFoundException");
      throw new DataNotFoundException("No Orchestration Store entries were found for consumer " + consumer.toString());
    } else {
      // Removing non-valid Store entries from the results
      List<OrchestrationStore> temp = new ArrayList<>();
      for (OrchestrationStore entry : retrievedList) {
        if (!entry.isValid()) {
          temp.add(entry);
        }
      }
      retrievedList.removeAll(temp);

      // Sorting the store entries based on their int priority field
      Collections.sort(retrievedList);
      log.info("queryOrchestrationStore returns " + retrievedList.size() + " orchestration store entries matching the criteria");
      return retrievedList;
    }
  }

  /**
   * Cross-checks the query results from the <i>Orchestration Store</i> with the <i>Service Registry</i> and <i>Authorization</i>. A provider
   * <tt>ArrowheadSystem</tt> has to be registered into the <i>Service Registry</i> at the time of the servicing request while being authorized too.
   *
   * @param srf The <tt>ServiceRequestForm</tt> from the requester <tt>ArrowheadSystem</tt>
   * @param entryList Result of the <i>Orchestration Store</i> query. All the entries matching the criteria provided by the
   *     <tt>ServiceRequestForm</tt>
   *
   * @return the list of <tt>OrchestrationStore</tt> objects which remained from the query after the cross-check
   */
  static List<OrchestrationStore> crossCheckStoreEntries(ServiceRequestForm srf, List<OrchestrationStore> entryList) {
    Map<String, Boolean> orchestrationFlags = srf.getOrchestrationFlags();
    Set<ArrowheadSystem> providerSystemsFromSR = new HashSet<>();
    Set<ArrowheadSystem> providerSystemsFromAuth;
    List<OrchestrationStore> toRemove = new ArrayList<>();

    // If true, the Orchestration Store was queried for default entries, meaning the service is different for each store entry
    if (srf.getRequestedService() == null) {
      for (OrchestrationStore entry : entryList) {
        // Querying the Service Registry for the current service
        List<ServiceRegistryEntry> srList = OrchestratorDriver
            .queryServiceRegistry(entry.getService(), orchestrationFlags.get("metadataSearch"), orchestrationFlags.get("pingProviders"));
        // Compiling the systems that provide the current service
        for (ServiceRegistryEntry srEntry : srList) {
          providerSystemsFromSR.add(srEntry.getProvider());
        }

        // Querying the Authorization to see if the provider system is authorized for this servicing or not
        providerSystemsFromAuth = OrchestratorDriver
            .queryAuthorization(entry.getConsumer(), entry.getService(), Collections.singleton(entry.getProviderSystem()));

        // Remove the Store entry from the list, if the SR or Auth crosscheck fails
        if (!providerSystemsFromSR.contains(entry.getProviderSystem()) || !providerSystemsFromAuth.contains(entry.getProviderSystem())) {
          toRemove.add(entry);
        }
      }
      entryList.removeAll(toRemove);
    }
    // Otherwise the service is fixed and we only need 1 SR and Auth query
    else {
      try {
        // Querying the Service Registry for the service
        List<ServiceRegistryEntry> srList = OrchestratorDriver
            .queryServiceRegistry(srf.getRequestedService(), orchestrationFlags.get("metadataSearch"), orchestrationFlags.get("pingProviders"));
        // Compiling the systems that provide the service
        for (ServiceRegistryEntry srEntry : srList) {
          providerSystemsFromSR.add(srEntry.getProvider());
        }

        //Compiling the list of intra-cloud provider systems from the store list for the auth query
        Set<ArrowheadSystem> localProviderSystems = new HashSet<>();
        for (OrchestrationStore entry : entryList) {
          if (entry.getProviderCloud() == null) {
            localProviderSystems.add(entry.getProviderSystem());
          }
        }
        // Querying the Authorization
        providerSystemsFromAuth = OrchestratorDriver.queryAuthorization(srf.getRequesterSystem(), srf.getRequestedService(), localProviderSystems);

        // Loop over the store entries and remove an entry, if the SR or Auth crosscheck fails
        for (OrchestrationStore entry : entryList) {
          if (entry.getProviderCloud() == null && (!providerSystemsFromSR.contains(entry.getProviderSystem()) || !providerSystemsFromAuth
              .contains(entry.getProviderSystem()))) {
            toRemove.add(entry);
          }
        }
        entryList.removeAll(toRemove);
      }
      /*
       * The SR or Auth query can throw DataNotFoundException, which has to be caught, in case there are inter-cloud store entries from the Store
       * query to check. Default store entries can only be intra-cloud, so the try/catch is only needed on the else branch.
       */ catch (DataNotFoundException e) {
        log.info("crossCheckStoreEntries catches DataNotFoundException from SR/Auth query");
        for (OrchestrationStore entry : entryList) {
          if (entry.getProviderCloud() == null) {
            toRemove.add(entry);
          }
        }
        entryList.removeAll(toRemove);
        return entryList;
      }
    }

    log.info("crossCheckStoreEntries returns " + entryList.size() + " orchestration store entries");
    return entryList;
  }

  /**
   * Initiates the Global Service Discovery process by sending a request to the Gatekeeper Core System.
   *
   * @param requestedService The <tt>ArrowheadService</tt> object representing the service for which the Gatekeeper will try to find a provider
   *     system
   * @param preferredClouds A list of <tt>ArrowheadCloud</tt>s which are preferred by the requester system for service consumption. If this list
   *     is empty, the Gatekeeper will send GSD poll requests to the <tt>NeighborCloud</tt>s instead.
   *
   * @return the GSD result from the Gatekeeper Core System
   *
   * @throws DataNotFoundException if none of the discovered <tt>ArrowheadCloud</tt>s returned back positive result
   */
  static GSDResult doGlobalServiceDiscovery(ArrowheadService requestedService, List<ArrowheadCloud> preferredClouds) {
    // Compiling the URI and the request payload
    String uri = Utility.getGatekeeperUri();
    uri = UriBuilder.fromPath(uri).path("init_gsd").toString();
    GSDRequestForm requestForm = new GSDRequestForm(requestedService, preferredClouds);

    // Sending the request, sanity check on the returned result
    Response response = Utility.sendRequest(uri, "PUT", requestForm);
    GSDResult result = response.readEntity(GSDResult.class);
    if (!result.isValid()) {
      log.error("doGlobalServiceDiscovery DataNotFoundException");
      throw new DataNotFoundException("GlobalServiceDiscovery yielded no result.");
    }

    log.info("doGlobalServiceDiscovery returns with " + result.getResponse().size() + " GSDAnswers");
    return result;
  }

  /**
   * Inter-Cloud matchmaking is mandatory for picking out a target Cloud to do ICN with. Clouds preferred by the consumer have higher priority. Custom
   * matchmaking algorithm can be implemented, as of now it just returns the first Cloud from the list.
   *
   * @param result The <tt>GSDResult</tt> object contains the <tt>ArrowheadCloud</tt>s which responded positively to the GSD polling.
   * @param preferredClouds The <tt>ArrowheadCloud</tt>s preferred by the requester <tt>ArrowheadSystem</tt>.
   * @param onlyPreferred An orchestration flags, indicating whether or not the requester <tt>ArrowheadSystem</tt> only wants to consume the
   *     <tt>ArrowheadService</tt> from a preferred provider.
   *
   * @return the target <tt>ArrowheadCloud</tt> for the ICN process
   *
   * @throws DataNotFoundException if there is no preferred provider Cloud available while <i>onlyPreferred</i> is set to true
   */
  static ArrowheadCloud interCloudMatchmaking(GSDResult result, List<ArrowheadCloud> preferredClouds, boolean onlyPreferred) {
    // Extracting the valid ArrowheadClouds from the GSDResult
    List<ArrowheadCloud> partnerClouds = new ArrayList<>();
    for (GSDAnswer answer : result.getResponse()) {
      if (answer.getProviderCloud().isValid()) {
        partnerClouds.add(answer.getProviderCloud());
      }
    }

    // partnerClouds.isEmpty() can only be true here if the other Gatekeepers returned not valid ArrowheadCloud objects
    if (!partnerClouds.isEmpty() && !preferredClouds.isEmpty()) {
      // We iterate through both ArrowheadCloud list, and return with 1 if we find a match.
      for (ArrowheadCloud preferredCloud : preferredClouds) {
        for (ArrowheadCloud partnerCloud : partnerClouds) {
          if (preferredCloud.equals(partnerCloud)) {
            log.info("interCloudMatchmaking: preferred Cloud found in the GSDResult");
            return partnerCloud;
          }
        }
      }
    }

    // No match was found, return the first ArrowheadCloud from the GSDResult if it is allowed by the orchestration flag.
    if (onlyPreferred) {
      log.error("interCloudMatchmaking DataNotFoundException, preferredClouds size: " + preferredClouds.size());
      throw new DataNotFoundException(
          "No preferred Cloud found in the GSD response. Inter-Cloud matchmaking failed, since only preferred providers are allowed.");
    }

    log.info("interCloudMatchmaking returns the first not preferred Cloud entry");
    return partnerClouds.get(0);
  }

  /**
   * Compiles an <tt>ICNRequestForm</tt> from the given parameters and initiates the Inter Cloud Negotiations process by sending a request to the
   * Gatekeeper Core System. The <tt>ICNRequestForm</tt> is a complex object containing all the necessary information to create a
   * <tt>ServiceRequestForm</tt> at the remote cloud.
   *
   * @param srf The <tt>ServiceRequestForm</tt> sent in by the requester <tt>ArrowheadSystem</tt>. 4 different fields of it is used in this
   *     method.
   * @param targetCloud The <tt>ArrowheadCloud</tt> entity this local cloud chose to do ICN with.
   *
   * @return a boxed {@link OrchestrationResponse} object from the remote cloud
   *
   * @throws DataNotFoundException if the ICN failed with the remote cloud for some reason
   */
  static ICNResult doInterCloudNegotiations(ServiceRequestForm srf, ArrowheadCloud targetCloud) {
    // Getting the list of valid preferred systems from the ServiceRequestForm, which belong to the target cloud
    List<ArrowheadSystem> preferredSystems = new ArrayList<>();
    for (PreferredProvider provider : srf.getPreferredProviders()) {
      if (provider.isGlobal() && provider.getProviderCloud().equals(targetCloud) && provider.getProviderSystem() != null && provider
          .getProviderSystem().isValid()) {
        preferredSystems.add(provider.getProviderSystem());
      }
    }

    // Passing through the relevant orchestration flags to the ICNRequestForm
    Map<String, Boolean> negotiationFlags = new HashMap<>();
    negotiationFlags.put("metadataSearch", srf.getOrchestrationFlags().get("metadataSearch"));
    negotiationFlags.put("pingProviders", srf.getOrchestrationFlags().get("pingProviders"));
    negotiationFlags.put("onlyPreferred", srf.getOrchestrationFlags().get("onlyPreferred"));
    negotiationFlags.put("externalServiceRequest", true);

    // Creating the ICNRequestForm object, which is the payload of the request sent to the Gatekeeper
    ICNRequestForm requestForm = new ICNRequestForm(srf.getRequestedService(), targetCloud, srf.getRequesterSystem(), preferredSystems,
                                                    negotiationFlags, null);

    // Compiling the URI, sending the request, doing sanity check on the returned result
    String uri = Utility.getGatekeeperUri();
    uri = UriBuilder.fromPath(uri).path("init_icn").toString();
    Response response = Utility.sendRequest(uri, "PUT", requestForm);
    ICNResult result = response.readEntity(ICNResult.class);
    if (!result.isValid()) {
      log.error("doInterCloudNegotiations DataNotFoundException");
      throw new DataNotFoundException("ICN failed with the remote cloud.");
    }

    log.info("doInterCloudNegotiations returns with " + result.getOrchResponse().getResponse().size() + " possible providers");
    return result;
  }

}
