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
package org.onebusaway.nyc.queue;

import com.eaio.uuid.UUID;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import com.google.gson.Gson;

import org.onebusaway.nyc.queue.model.RealtimeEnvelope;
import org.onebusaway.nyc.queue.Publisher;
import org.junit.Test;

import lrms_final_09_07.Angle;
import tcip_final_3_0_5_1.CcLocationReport;
import tcip_final_3_0_5_1.CPTOperatorIden;
import tcip_final_3_0_5_1.CPTVehicleIden;
import tcip_final_3_0_5_1.SCHRouteIden;
import tcip_final_3_0_5_1.SCHRunIden;
import tcip_final_3_0_5_1.SPDataQuality;
import tcip_3_0_5_local.NMEA;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;


public class PublisherTest {

  @Test
  public void testOutput() {
      RealtimeEnvelope re = new RealtimeEnvelope();
      re.setUUID(new UUID().toString());
      re.setTimeReceived(System.currentTimeMillis());
      re.setCcLocationReport(buildCcLocationReport());
      Gson gson = new Gson();
      gson.toJson(re);
  }

  @Test
  public void testWrap() throws Exception {
			
			Publisher p = new Publisher("topic") {
							String generateUUID() {
									return "foo";
							}
							long getTimeReceived() {
									return 1234567;
							}
					};
			ObjectMapper _mapper = new ObjectMapper();
			/* use Jaxb annotation interceptor so we pick up autogenerated annotations from XSDs */
			AnnotationIntrospector introspector = new JaxbAnnotationIntrospector();
			_mapper.setAnnotationIntrospector(introspector);

			String ccmessage = "{\"CcLocationReport\": {\"request-id\" : 528271,\"vehicle\": {\"vehicle-id\": 7579,\"agency-id\": 2008,\"agencydesignator\": \"MTA NYCT\"},\"status-info\": 0,\"time-reported\": \"2011-10-15T03:26:19.000-00:00\",\"latitude\": 40612060,\"longitude\": -74035771,\"direction\": {\"deg\": 128.77},\"speed\": 0,\"manufacturer-data\": \"VFTP123456789\",\"operatorID\": {\"operator-id\": 0,\"designator\": \"\"},\"runID\": {\"run-id\": 0,\"designator\": \"\"},\"destSignCode\": 4631,\"routeID\": {\"route-id\": 0,\"route-designator\": \"\"},\"localCcLocationReport\": {\"NMEA\": {\"sentence\": [\"\",\"\"]}}}}";
			String rtmessage = "{\"RealtimeEnvelope\": {\"UUID\":\"foo\",\"timeReceived\": 1234567,\"CcLocationReport\": {\"request-id\" : 528271,\"vehicle\": {\"vehicle-id\": 7579,\"agency-id\": 2008,\"agencydesignator\": \"MTA NYCT\"},\"status-info\": 0,\"time-reported\": \"2011-10-15T03:26:19.000-00:00\",\"latitude\": 40612060,\"longitude\": -74035771,\"direction\": {\"deg\": 128.77},\"speed\": 0,\"manufacturer-data\": \"VFTP123456789\",\"operatorID\": {\"operator-id\": 0,\"designator\": \"\"},\"runID\": {\"run-id\": 0,\"designator\": \"\"},\"destSignCode\": 4631,\"routeID\": {\"route-id\": 0,\"route-designator\": \"\"},\"localCcLocationReport\": {\"NMEA\": {\"sentence\": [\"\",\"\"]}}}}}";

			//System.out.println("initial jackson=\n" + rtmessage);
      // now wrap message
      String envelope = p.wrap(ccmessage.getBytes());
			//System.out.println("wrapped message=\n" + envelope);
			// check what wrap sends us
			assertEquals(rtmessage, envelope);
      // now deserialize validating JSON 
      JsonNode wrappedMessage = _mapper.readValue(envelope, JsonNode.class);
      String realtimeEnvelopeString = wrappedMessage.get("RealtimeEnvelope").toString();
			//System.out.println("realtimeEnvelopeString=\n" + realtimeEnvelopeString);
			RealtimeEnvelope env = _mapper.readValue(realtimeEnvelopeString, RealtimeEnvelope.class);      
  }

  @Test
	public void testWrapWithNewLine() throws Exception  {
			Publisher p = new Publisher("topic") {
							String generateUUID() {
									return "foo";
							}
							long getTimeReceived() {
									return 1234567;
							}
					};
			ObjectMapper _mapper = new ObjectMapper();
			/* use Jaxb annotation interceptor so we pick up autogenerated annotations from XSDs */
			AnnotationIntrospector introspector = new JaxbAnnotationIntrospector();
			_mapper.setAnnotationIntrospector(introspector);

			String ccmessage = "{\"CcLocationReport\": {\"request-id\" : 528271,\"vehicle\": {\"vehicle-id\": 7579,\"agency-id\": 2008,\"agencydesignator\": \"MTA NYCT\"},\"status-info\": 0,\"time-reported\": \"2011-10-15T03:26:19.000-00:00\",\"latitude\": 40612060,\"longitude\": -74035771,\"direction\": {\"deg\": 128.77},\"speed\": 0,\"manufacturer-data\": \"VFTP123456789\",\"operatorID\": {\"operator-id\": 0,\"designator\": \"\"},\"runID\": {\"run-id\": 0,\"designator\": \"\"},\"destSignCode\": 4631,\"routeID\": {\"route-id\": 0,\"route-designator\": \"\"},\"localCcLocationReport\": {\"NMEA\": {\"sentence\": [\"\",\"\"]}}}}\n";
			String rtmessage = "{\"RealtimeEnvelope\": {\"UUID\":\"foo\",\"timeReceived\": 1234567,\"CcLocationReport\": {\"request-id\" : 528271,\"vehicle\": {\"vehicle-id\": 7579,\"agency-id\": 2008,\"agencydesignator\": \"MTA NYCT\"},\"status-info\": 0,\"time-reported\": \"2011-10-15T03:26:19.000-00:00\",\"latitude\": 40612060,\"longitude\": -74035771,\"direction\": {\"deg\": 128.77},\"speed\": 0,\"manufacturer-data\": \"VFTP123456789\",\"operatorID\": {\"operator-id\": 0,\"designator\": \"\"},\"runID\": {\"run-id\": 0,\"designator\": \"\"},\"destSignCode\": 4631,\"routeID\": {\"route-id\": 0,\"route-designator\": \"\"},\"localCcLocationReport\": {\"NMEA\": {\"sentence\": [\"\",\"\"]}}}}}";

			//System.out.println("initial jackson=\n" + rtmessage);
      // now wrap message
      String envelope = p.wrap(ccmessage.getBytes());
			//System.out.println("wrapped message=\n" + envelope);
			// check what wrap sends us
			assertEquals(rtmessage, envelope);
	}


  @Test
  public void testWrapNull() throws Exception {
			
			Publisher p = new Publisher("topic") {
							String generateUUID() {
									return "foo";
							}
							long getTimeReceived() {
									return 1234567;
							}
					};

      String envelope = p.wrap(null);
      assertEquals(null, envelope);
  }

  @Test
  public void testWrapEmpty() throws Exception {
			
			Publisher p = new Publisher("topic") {
							String generateUUID() {
									return "foo";
							}
							long getTimeReceived() {
									return 1234567;
							}
					};

      String envelope = p.wrap(new byte[0]);
      assertEquals(null, envelope);
  }

  @Test
  public void testWrapSingle() throws Exception {
			
			Publisher p = new Publisher("topic") {
							String generateUUID() {
									return "foo";
							}
							long getTimeReceived() {
									return 1234567;
							}
					};

      byte[] buff = " ".getBytes();
      String envelope = p.wrap(buff);
      assertEquals(null, envelope);
  }


  private CcLocationReport buildCcLocationReport() {
      CcLocationReport m = new CcLocationReport();
      m.setRequestId(1205);
      m.setDataQuality(new SPDataQuality());
      m.getDataQuality().setQualitativeIndicator("4");
      m.setDestSignCode(4631l);
      m.setDirection(new Angle());
      m.getDirection().setDeg(new BigDecimal(128.77));
      m.setLatitude(40640760);
      m.setLongitude(-74018234);
      m.setManufacturerData("VFTP123456789");
      m.setOperatorID(new CPTOperatorIden());
      m.getOperatorID().setOperatorId(0);
      m.getOperatorID().setDesignator("123456");
      m.setRequestId(1);
      m.setRouteID(new SCHRouteIden());
      m.getRouteID().setRouteId(0);
      m.getRouteID().setRouteDesignator("63");
      m.setRunID(new SCHRunIden());
      m.getRunID().setRunId(0);
      m.getRunID().setDesignator("1");
      m.setSpeed((short)36);
      m.setStatusInfo(0);
      m.setTimeReported("2011-06-22T10:58:10.0-00:00");
      m.setVehicle(new CPTVehicleIden());
      m.getVehicle().setAgencydesignator("MTA NYCT");
      m.getVehicle().setAgencyId(2008l);
      m.getVehicle().setVehicleId(2560);
      m.setLocalCcLocationReport(new tcip_3_0_5_local.CcLocationReport());
      m.getLocalCcLocationReport().setNMEA(new NMEA());
      m.getLocalCcLocationReport().getNMEA().getSentence().add("$GPRMC,105850.00,A,4038.445646,N,07401.094043,W,002.642,128.77,220611,,,A*7C");
      m.getLocalCcLocationReport().getNMEA().getSentence().add("$GPGGA,105850.000,4038.44565,N,07401.09404,W,1,09,01.7,+00042.0,M,,M,,*49");
      return m;
  }
    
}
