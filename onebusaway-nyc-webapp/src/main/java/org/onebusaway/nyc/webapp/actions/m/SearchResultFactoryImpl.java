/**
 * Copyright (c) 2011 Metropolitan Transportation Authority
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.onebusaway.nyc.webapp.actions.m;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.onebusaway.nyc.geocoder.service.NycGeocoderResult;
import org.onebusaway.nyc.presentation.impl.search.AbstractSearchResultFactoryImpl;
import org.onebusaway.nyc.presentation.model.SearchResult;
import org.onebusaway.nyc.presentation.service.realtime.RealtimeService;
import org.onebusaway.nyc.presentation.service.search.SearchResultFactory;
import org.onebusaway.nyc.siri.support.SiriDistanceExtension;
import org.onebusaway.nyc.siri.support.SiriExtensionWrapper;
import org.onebusaway.nyc.transit_data.services.NycTransitDataService;
import org.onebusaway.nyc.util.configuration.ConfigurationService;
import org.onebusaway.nyc.webapp.actions.m.model.GeocodeResult;
import org.onebusaway.nyc.webapp.actions.m.model.RouteAtStop;
import org.onebusaway.nyc.webapp.actions.m.model.RouteDirection;
import org.onebusaway.nyc.webapp.actions.m.model.RouteInRegionResult;
import org.onebusaway.nyc.webapp.actions.m.model.RouteResult;
import org.onebusaway.nyc.webapp.actions.m.model.StopOnRoute;
import org.onebusaway.nyc.webapp.actions.m.model.StopResult;
import org.onebusaway.transit_data.model.NameBean;
import org.onebusaway.transit_data.model.RouteBean;
import org.onebusaway.transit_data.model.StopBean;
import org.onebusaway.transit_data.model.StopGroupBean;
import org.onebusaway.transit_data.model.StopGroupingBean;
import org.onebusaway.transit_data.model.StopsForRouteBean;
import org.onebusaway.transit_data.model.service_alerts.ServiceAlertBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.org.siri.siri.MonitoredCallStructure;
import uk.org.siri.siri.MonitoredStopVisitStructure;
import uk.org.siri.siri.MonitoredVehicleJourneyStructure;
import uk.org.siri.siri.NaturalLanguageStringStructure;
import uk.org.siri.siri.VehicleActivityStructure;

public class SearchResultFactoryImpl extends AbstractSearchResultFactoryImpl implements SearchResultFactory {

  private static Logger _log = LoggerFactory.getLogger(SearchResultFactoryImpl.class);
  
  private ConfigurationService _configurationService;

  private RealtimeService _realtimeService;

  private NycTransitDataService _nycTransitDataService;
  
  private static final String PROGRESS_LAYOVER = "layover";
  private static final String PROGRESS_PREV_TRIP = "prevTrip";

  public SearchResultFactoryImpl(NycTransitDataService nycTransitDataService,
      RealtimeService realtimeService, ConfigurationService configurationService) {
    _nycTransitDataService = nycTransitDataService;
    _realtimeService = realtimeService;
    _configurationService = configurationService;
  }

  @Override
  public SearchResult getRouteResultForRegion(RouteBean routeBean) {
    return new RouteInRegionResult(routeBean);
  }

  @Override
  public SearchResult getRouteResult(RouteBean routeBean) {
    List<RouteDirection> directions = new ArrayList<RouteDirection>();

    StopsForRouteBean stopsForRoute = _nycTransitDataService.getStopsForRoute(routeBean.getId());

    // create stop ID->stop bean map
    Map<String, StopBean> stopIdToStopBeanMap = new HashMap<String, StopBean>();
    for (StopBean stopBean : stopsForRoute.getStops()) {
      stopIdToStopBeanMap.put(stopBean.getId(), stopBean);
    }

    // add stops in both directions
    Map<String, List<String>> stopIdAndDirectionToDistanceAwayStringMap = getStopIdAndDirectionToDistanceAwayStringsListMapForRoute(routeBean);

    List<StopGroupingBean> stopGroupings = stopsForRoute.getStopGroupings();
    for (StopGroupingBean stopGroupingBean : stopGroupings) {
      for (StopGroupBean stopGroupBean : stopGroupingBean.getStopGroups()) {
        NameBean name = stopGroupBean.getName();
        String type = name.getType();

        if (!type.equals("destination"))
          continue;

        // service in this direction
        Boolean hasUpcomingScheduledService = _nycTransitDataService.routeHasUpcomingScheduledService(
        	(routeBean.getAgency()!=null?routeBean.getAgency().getId():null),	
            System.currentTimeMillis(), routeBean.getId(),
            stopGroupBean.getId());

        // if there are buses on route, always have "scheduled service"
        Boolean routeHasVehiclesInService = 
      		  _realtimeService.getVehiclesInServiceForRoute(routeBean.getId(), stopGroupBean.getId(), System.currentTimeMillis());

        if(routeHasVehiclesInService) {
      	  hasUpcomingScheduledService = true;
        }

        // stops in this direction
        List<StopOnRoute> stopsOnRoute = null;
        if (!stopGroupBean.getStopIds().isEmpty()) {
          stopsOnRoute = new ArrayList<StopOnRoute>();

          for (String stopId : stopGroupBean.getStopIds()) { 
        	  if (_nycTransitDataService.stopHasRevenueServiceOnRoute((routeBean.getAgency()!=null?routeBean.getAgency().getId():null),
                      stopId, routeBean.getId(), stopGroupBean.getId())) {
                stopsOnRoute.add(new StopOnRoute(stopIdToStopBeanMap.get(stopId),
            		stopIdAndDirectionToDistanceAwayStringMap.get(stopId + "_" + stopGroupBean.getId())));
              }  
          }
        }

        directions.add(new RouteDirection(stopGroupBean.getName().getName(), stopGroupBean, stopsOnRoute,
            hasUpcomingScheduledService, null));
      }
    }

    // service alerts in this direction
    Set<String> serviceAlertDescriptions = new HashSet<String>();

    List<ServiceAlertBean> serviceAlertBeans = _realtimeService.getServiceAlertsForRoute(routeBean.getId());
    populateServiceAlerts(serviceAlertDescriptions, serviceAlertBeans);

    return new RouteResult(routeBean, directions, serviceAlertDescriptions);
  }

  @Override
  // TODO: this method needs refactoring to consider route and direction for routes with loops
  public SearchResult getStopResult(StopBean stopBean, Set<RouteBean> routeFilter) {
    List<RouteAtStop> routesWithArrivals = new ArrayList<RouteAtStop>();
    List<RouteAtStop> routesWithNoVehiclesEnRoute = new ArrayList<RouteAtStop>();
    List<RouteAtStop> routesWithNoScheduledService = new ArrayList<RouteAtStop>();
    List<RouteBean> filteredRoutes = new ArrayList<RouteBean>();
    
    Set<String> serviceAlertDescriptions = new HashSet<String>();

    for (RouteBean routeBean : stopBean.getRoutes()) {
      if (routeFilter != null && !routeFilter.isEmpty()
          && !routeFilter.contains(routeBean)) {
        filteredRoutes.add(routeBean);
        continue;
      }

      StopsForRouteBean stopsForRoute = _nycTransitDataService.getStopsForRoute(routeBean.getId());

      List<RouteDirection> directions = new ArrayList<RouteDirection>();
      List<StopGroupingBean> stopGroupings = stopsForRoute.getStopGroupings();
      for (StopGroupingBean stopGroupingBean : stopGroupings) {
        for (StopGroupBean stopGroupBean : stopGroupingBean.getStopGroups()) {
          NameBean name = stopGroupBean.getName();
          String type = name.getType();

          if (!type.equals("destination"))
            continue;

          // filter out route directions that don't stop at this stop
          if (!stopGroupBean.getStopIds().contains(stopBean.getId()))
            continue;

          // arrivals in this direction
          Map<String, List<String>> arrivalsForRouteAndDirection = getDisplayStringsByHeadsignForStopAndRouteAndDirection(
              stopBean, routeBean, stopGroupBean);

          // service alerts for this route + direction
          List<ServiceAlertBean> serviceAlertBeans = _realtimeService.getServiceAlertsForRouteAndDirection(
              routeBean.getId(), stopGroupBean.getId());
          populateServiceAlerts(serviceAlertDescriptions, serviceAlertBeans);
          
          // service in this direction
          Boolean hasUpcomingScheduledService = _nycTransitDataService.stopHasUpcomingScheduledService(
        	  (routeBean.getAgency()!=null?routeBean.getAgency().getId():null),
              System.currentTimeMillis(), stopBean.getId(), routeBean.getId(),
              stopGroupBean.getId());

          // if there are buses on route, always have "scheduled service"
          if (!arrivalsForRouteAndDirection.isEmpty()) {
            hasUpcomingScheduledService = true;
          }

          if(arrivalsForRouteAndDirection.isEmpty()) {
            directions.add(new RouteDirection(stopGroupBean.getName().getName(), stopGroupBean, null, 
                hasUpcomingScheduledService, Collections.<String>emptyList()));
          } else {          
            for (Map.Entry<String,List<String>> entry : arrivalsForRouteAndDirection.entrySet()) {
              directions.add(new RouteDirection(entry.getKey(), stopGroupBean, null, 
                 hasUpcomingScheduledService, entry.getValue()));
            }
          }
        }
      }

      RouteAtStop routeAtStop = new RouteAtStop(routeBean, directions,
          serviceAlertDescriptions);

      // Keep track of service and no service per route and direction. 
      // in case the route has loops, we must consider both directions
      boolean arrivalsFound = false;
      boolean nssm = false;
      for (RouteDirection direction : routeAtStop.getDirections()) {
        if (direction.getHasUpcomingScheduledService() == false 
            && direction.getDistanceAways().isEmpty()) {
          nssm = true;
        } else {
          if (!direction.getDistanceAways().isEmpty()) {
            arrivalsFound = true;
            break;
          }
        }
      }
      if (arrivalsFound) {
        routesWithArrivals.add(routeAtStop);
      } else if (nssm) {
        routesWithNoScheduledService.add(routeAtStop);
      } else {
        routesWithNoVehiclesEnRoute.add(routeAtStop);
      }
    }

    
    
    return new StopResult(stopBean, routesWithArrivals,
        routesWithNoVehiclesEnRoute, routesWithNoScheduledService, filteredRoutes, serviceAlertDescriptions);
  }

  @Override
  public SearchResult getGeocoderResult(NycGeocoderResult geocodeResult, Set<RouteBean> routeFilter) {
    return new GeocodeResult(geocodeResult);
  }

  // stop view
  private Map<String, List<String>> getDisplayStringsByHeadsignForStopAndRouteAndDirection(
      StopBean stopBean, RouteBean routeBean, StopGroupBean stopGroupBean) {
    
    Map<String, List<String>> results = new HashMap<String, List<String>>();

    // stop visits
    List<MonitoredStopVisitStructure> visitList = _realtimeService.getMonitoredStopVisitsForStop(
        stopBean.getId(), 0, System.currentTimeMillis());

    for (MonitoredStopVisitStructure visit : visitList) {
      String routeId = visit.getMonitoredVehicleJourney().getLineRef().getValue();
      if (!routeBean.getId().equals(routeId))
        continue;

      String directionId = visit.getMonitoredVehicleJourney().getDirectionRef().getValue();
      if (!stopGroupBean.getId().equals(directionId))
        continue;

      // on detour?
      MonitoredCallStructure monitoredCall = visit.getMonitoredVehicleJourney().getMonitoredCall();
      if (monitoredCall == null)
        continue;

      if (!results.containsKey(visit.getMonitoredVehicleJourney().getDestinationName().getValue()))
        results.put(visit.getMonitoredVehicleJourney().getDestinationName().getValue(), new ArrayList<String>());
      
      if(results.get(visit.getMonitoredVehicleJourney().getDestinationName().getValue()).size() >= 3)
        continue;
      
      String distance = getPresentableDistance(visit.getMonitoredVehicleJourney(),
    		visit.getRecordedAtTime().getTime(), true);
    	  
      String timePrediction = getPresentableTime(visit.getMonitoredVehicleJourney(),
    	 	visit.getRecordedAtTime().getTime(), true);

      if(timePrediction != null) {
        results.get(visit.getMonitoredVehicleJourney().getDestinationName().getValue()).add(timePrediction);
      } else {
        results.get(visit.getMonitoredVehicleJourney().getDestinationName().getValue()).add(distance);
      }
    }

    return results;
  }

  // route view
  private Map<String, List<String>> getStopIdAndDirectionToDistanceAwayStringsListMapForRoute(
      RouteBean routeBean) {
    Map<String, List<String>> result = new HashMap<String, List<String>>();

    // stop visits
    List<VehicleActivityStructure> journeyList = _realtimeService.getVehicleActivityForRoute(
        routeBean.getId(), null, 0, System.currentTimeMillis());

    // build map of stop IDs to list of distance strings
    for (VehicleActivityStructure journey : journeyList) {
      // on detour?
      MonitoredCallStructure monitoredCall = journey.getMonitoredVehicleJourney().getMonitoredCall();
      if (monitoredCall == null) {
        continue;
      }

      String direction = journey.getMonitoredVehicleJourney().getDirectionRef().getValue();
      String stopId = monitoredCall.getStopPointRef().getValue();

      List<String> distanceStrings = result.get(stopId);
      if (distanceStrings == null) {
        distanceStrings = new ArrayList<String>();
      }

      distanceStrings.add(getPresentableDistance(
          journey.getMonitoredVehicleJourney(),
          journey.getRecordedAtTime().getTime(), false));
      result.put(stopId + "_" + direction, distanceStrings);
    }

    return result;
  }

  private String getPresentableTime(
		  MonitoredVehicleJourneyStructure journey, long updateTime,
	      boolean isStopContext) {

	  NaturalLanguageStringStructure progressStatus = journey.getProgressStatus();
	  MonitoredCallStructure monitoredCall = journey.getMonitoredCall();
	  
	  if(!isStopContext) {
		  return null;
	  }
	  
	  int staleTimeout = _configurationService.getConfigurationValueAsInteger("display.staleTimeout", 120);
	  long age = (System.currentTimeMillis() - updateTime) / 1000;

	  if (age > staleTimeout) {
		  return null;
	  }
	  
	  if(monitoredCall.getExpectedArrivalTime() != null) {
		  long predictedArrival = monitoredCall.getExpectedArrivalTime().getTime();

		  SiriExtensionWrapper wrapper = (SiriExtensionWrapper) monitoredCall.getExtensions().getAny();
		  SiriDistanceExtension distanceExtension = wrapper.getDistances();
		  String distance = distanceExtension.getPresentableDistance();
		  String distanceBold = "<strong>" + distance + "</strong>";

		  double minutes = Math.floor((predictedArrival - updateTime) / 60 / 1000);
		  String timeString = Math.round(minutes) + " minute" + ((Math.abs(minutes) != 1) ? "s" : "");
		  String timeAndDistance = "<strong>" + timeString + "</strong>," + distance;
		  String loadOccupancy = getPresentableOccupancy(journey, updateTime);
		    
		  Date originDepartureTime = journey.getOriginAimedDepartureTime();

		  if(originDepartureTime != null && isLayover(progressStatus)){
			  if(isDepartureOnSchedule(originDepartureTime)){
				  return distanceBold + " (at terminal, scheduled to depart at " + 
						  getFormattedOriginAimedDepartureTime(originDepartureTime) + ")";
			  }
			  
			  return distanceBold + " (at terminal)";
		  }
		  else if(originDepartureTime != null && isPrevTrip(progressStatus)) {
			  if(isDepartureOnSchedule(originDepartureTime)) {
		    		return distanceBold + " (+ layover, scheduled to depart terminal at " 
		    				+ getFormattedOriginAimedDepartureTime(originDepartureTime);
		    	}
		    	else{
		    		return distanceBold + " (+ scheduled layover at terminal)";
		    	}
	  	  }
		  else {
			  return timeAndDistance + loadOccupancy;
		  }
	  }
	  
	  return null;
  }
  
  
  private String getPresentableOccupancy(
			MonitoredVehicleJourneyStructure journey, long updateTime) {
	  	

		// if data is old, no occupancy
		int staleTimeout = _configurationService.getConfigurationValueAsInteger("display.staleTimeout", 120);
		long age = (System.currentTimeMillis() - updateTime) / 1000;

		if (age > staleTimeout) {
			return "";
		}
		
		if(journey.getOccupancy() != null) {
			
			String loadOccupancy = journey.getOccupancy().toString();
			loadOccupancy = loadOccupancy.toUpperCase();
			//TODO: Modify output load text here
			if(loadOccupancy.equals("SEATS_AVAILABLE") || loadOccupancy.equals("MANY_SEATS_AVAILABLE"))
				loadOccupancy = "seats available";
			else if (loadOccupancy.equals("FEW_SEATS_AVAILABLE"))
				loadOccupancy = "almost full";
			else if (loadOccupancy.equals("FULL"))
				loadOccupancy = "full";
			
			return "<strong>, ("+loadOccupancy+")</strong>" ;
		}else
			return "";

	}
  
  private String getPresentableDistance(
      MonitoredVehicleJourneyStructure journey, long updateTime,
      boolean isStopContext) {
    MonitoredCallStructure monitoredCall = journey.getMonitoredCall();
    SiriExtensionWrapper wrapper = (SiriExtensionWrapper) monitoredCall.getExtensions().getAny();
    SiriDistanceExtension distanceExtension = wrapper.getDistances();

    String message = "";
    String distance = "<strong>" + distanceExtension.getPresentableDistance() + "</strong>";
    String loadOccupancy = getPresentableOccupancy(journey, updateTime);
    
    NaturalLanguageStringStructure progressStatus = journey.getProgressStatus();
    Date originDepartureTime = journey.getOriginAimedDepartureTime();
    
    if(originDepartureTime != null){
	    // at terminal label only appears in stop results
	    if (isStopContext && isLayover(progressStatus)) {
	    	if(isDepartureOnSchedule(originDepartureTime)) {
	    		message += "at terminal, scheduled to depart at " 
	    				+ getFormattedOriginAimedDepartureTime(originDepartureTime);
	    	}
	    	else{
	        	message += "at terminal";
	    	}
	        
	    } else if (isStopContext && isPrevTrip(progressStatus)) {
	    	if(isDepartureOnSchedule(originDepartureTime)) {
	    		message += "+ layover, scheduled to depart terminal at " 
	    				+ getFormattedOriginAimedDepartureTime(originDepartureTime);
	    	}
	    	else{
	    		message += "+ scheduled layover at terminal";
	    	}
	    }
    }
    	
    int staleTimeout = _configurationService.getConfigurationValueAsInteger(
        "display.staleTimeout", 120);
    long age = (System.currentTimeMillis() - updateTime) / 1000;

    if (age > staleTimeout) {
      if (message.length() > 0) {
        message += ", ";
      }

      message += "old data";
    }

    if (message.length() > 0)
      return distance + " (" + message + ")";
    else
      return distance + loadOccupancy;
  }
  
  private boolean isLayover(NaturalLanguageStringStructure progressStatus){
	  return progressStatus != null && progressStatus.getValue().contains(PROGRESS_LAYOVER);
  }
  
  private boolean isPrevTrip(NaturalLanguageStringStructure progressStatus){
	  return progressStatus != null && progressStatus.getValue().contains(PROGRESS_PREV_TRIP);
  }
  
  private boolean isDepartureOnSchedule(Date departureDate){
	  return departureDate != null && departureDate.getTime() > System.currentTimeMillis();
  }
  
  private String getFormattedOriginAimedDepartureTime(Date originAimedDepartureTime){
	  DateFormat formatter = DateFormat.getTimeInstance(DateFormat.SHORT);
	  return formatter.format(originAimedDepartureTime);
  }
}
