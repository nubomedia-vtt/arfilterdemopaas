/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 */
//Created 2014-12-01
package fi.vtt.nubomedia.kurento;
import java.io.File;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

//import org.kurento.client.KurentoObject;
//import org.kurento.client.factory.KurentoClient;
import org.kurento.client.EventListener;
import org.kurento.client.IceCandidate;
import org.kurento.client.KurentoClient;
//import org.kurento.client.FaceOverlayFilter;
import org.kurento.client.MediaPipeline;
import org.kurento.client.OnIceCandidateEvent;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/*
import org.kurento.module.armarkerdetector.ArKvpFloat;
import org.kurento.module.armarkerdetector.ArKvpInteger;
import org.kurento.module.armarkerdetector.ArKvpString;
import org.kurento.module.armarkerdetector.ArMarkerdetector;
import org.kurento.module.armarkerdetector.MarkerCountEvent;
import org.kurento.module.armarkerdetector.MarkerPoseEvent;
import org.kurento.module.armarkerdetector.TickEvent;
import org.kurento.module.armarkerdetector.ArMarkerPose;
import org.kurento.module.armarkerdetector.ArThing;
import org.kurento.module.armarkerdetector.OverlayType;
*/

import fi.vtt.nubomedia.kurento.module.armarkerdetector.ArKvpFloat;
import fi.vtt.nubomedia.kurento.module.armarkerdetector.ArKvpInteger;
import fi.vtt.nubomedia.kurento.module.armarkerdetector.ArKvpString;
import fi.vtt.nubomedia.kurento.module.armarkerdetector.ArMarkerdetector;
import fi.vtt.nubomedia.kurento.module.armarkerdetector.MarkerCountEvent;
import fi.vtt.nubomedia.kurento.module.armarkerdetector.MarkerPoseEvent;
import fi.vtt.nubomedia.kurento.module.armarkerdetector.TickEvent;
import fi.vtt.nubomedia.kurento.module.armarkerdetector.ArMarkerPose;
import fi.vtt.nubomedia.kurento.module.armarkerdetector.ArThing;
import fi.vtt.nubomedia.kurento.module.armarkerdetector.OverlayType;



import org.kurento.client.*;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.DefaultRealMatrixChangingVisitor;
import org.apache.commons.math3.linear.MatrixUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * armarkerdetector handler
 * 
 * @author Markus Ylikerala
 */
public class Ar3DHandler extends TextWebSocketHandler {	
	private final Logger log = LoggerFactory.getLogger(Ar3DHandler.class);
	private static final Gson gson = new GsonBuilder().create();
	private ConcurrentHashMap<String, MediaPipeline> pipelines = new ConcurrentHashMap<String, MediaPipeline>();
	private String jsonFile;
	private final ConcurrentHashMap<String, UserSession> users = new ConcurrentHashMap<String, UserSession>();
    private WebRtcEndpoint webRtcEndpoint;

    private PrintWriter out;
	
	@Autowired
	private KurentoClient kurento;

	@Override
	public void handleTextMessage(WebSocketSession session, TextMessage message)
			throws Exception {
		log.debug("ME HadleTextMsg");

		JsonObject jsonMessage = gson.fromJson(message.getPayload(),
				JsonObject.class);
		
		log.debug("Incoming message: {}", jsonMessage);

		switch (jsonMessage.get("id").getAsString()) {
		case "get_stats":			
			getStats(session);
			break;
		case "start":
			start(session, jsonMessage);
			break;
		case "reload":
			reload(session, jsonMessage);
			break;
		case "pose":
			pose(session, jsonMessage);
			break;
		case "stop":
			String sessionId = session.getId();
			if (pipelines.containsKey(sessionId)) {
				pipelines.get(sessionId).release();
				pipelines.remove(sessionId);
			}
			break;

		case "onIceCandidate": 
		    JsonObject jsonCandidate = jsonMessage.get("candidate").getAsJsonObject();
		    
		    UserSession user = users.get(session.getId());
		    if (user != null) {
			IceCandidate candidate = new IceCandidate(jsonCandidate.get("candidate").getAsString(),
								  jsonCandidate.get("sdpMid").getAsString(), jsonCandidate.get("sdpMLineIndex").getAsInt());
			user.addCandidate(candidate);
			}
		    break;
		default:
			sendError(session,
					"Invalid message with id "
							+ jsonMessage.get("id").getAsString());
			break;
		}
	}

	private java.util.List<ArThing> createArThings(String json) throws IOException{		
		JsonObject jsonMessage = gson.fromJson(json, JsonObject.class);
		return createArThings(jsonMessage);
	}

	private String getFile(String path) throws IOException  {
		RandomAccessFile in = new RandomAccessFile(new File(path), "r");
		FileChannel ch = in.getChannel();
		long size = ch.size();   
		byte[] buf = new byte[(int)size];
		in.read(buf, 0, buf.length);
		in.close();
		return new String(buf);
	}

	private java.util.List<ArThing> createArThings(JsonObject jsonObjects){
		System.err.println(jsonObjects);
		List<ArThing> arThings = new ArrayList<ArThing>();
		JsonArray  jsonArray = jsonObjects.getAsJsonArray("augmentables");

		Iterator<JsonElement> itr = jsonArray.iterator();
		while(itr.hasNext()){
			JsonElement jsonElm = itr.next();		
			if(jsonElm.isJsonNull()){
			    System.err.println("Really Skip null");
			    continue;
			}
			System.err.println("Skip: " + jsonElm);

			JsonObject	jsonObject = jsonElm.getAsJsonObject();			

			int id = jsonObject.get("id").getAsInt();
			OverlayType augmentableType;
			switch(jsonObject.get("type").getAsString()){
			case "2D":
				augmentableType = OverlayType.TYPE2D;
				break;
			case "3D":
				augmentableType = OverlayType.TYPE3D;
				break;
			default:
				throw new RuntimeException("Bizarre OverlayType: " + jsonObject.get("type").getAsString());
			}
			List<ArKvpString> strings = new ArrayList<ArKvpString>();
			List<ArKvpFloat> floats = new ArrayList<ArKvpFloat>();
			List<ArKvpInteger> integers = new ArrayList<ArKvpInteger>();
			createKVPs(jsonObject, strings, integers, floats);

			ArThing arThing = new ArThing(id, augmentableType, strings, integers, floats);
			arThings.add(arThing);
		}
		return arThings;

		//		return createArThings(new int[]{
		//			0, 
		//			1, 
		//			2
		//		    }, 
		//		    new OverlayType[]{
		//			OverlayType.TYPE3D, 
		//			OverlayType.TYPE3D, 
		//			OverlayType.TYPE2D
		//		    },
		//		    new String[]{
		//			
		//			//"http://130.188.198.150:9090/icosahedron.ply", 
		//			"/opt/teapot.ply", 
		//			"/opt/cube.ply", 
		//			"/opt/propex.png"
		//		    },
		//		    new float[]{
		//			1.0f, 
		//			0.05f, 
		//			1.0f
		//		    },
		//		    new String[]{
		//			"", 
		//			"", 
		//			"snafu"
		//		    });
	}


	private void createKVPs(JsonObject jsonObject, List<ArKvpString> strings, List<ArKvpInteger> integers, List<ArKvpFloat> floats) {
		for(String kvpId : new String[]{"strings", "ints", "floats"}){
			JsonElement kvp = jsonObject.get(kvpId);
			if(kvp == null){
				continue;
			}
			Iterator<JsonElement> itr = kvp.getAsJsonArray().iterator();
			while(itr.hasNext()){
				JsonElement jsonElm = itr.next();
				Set<Map.Entry<String, JsonElement>> pairs = jsonElm.getAsJsonObject().entrySet();
				for(Map.Entry<String, JsonElement> map : pairs){
					switch(kvpId){
					case "strings":				
						strings.add(new ArKvpString(map.getKey(), map.getValue().getAsString()));
						break;
					case "ints":				
						integers.add(new ArKvpInteger(map.getKey(), map.getValue().getAsInt()));
						break;
					case "floats":				
						floats.add(new ArKvpFloat(map.getKey(), map.getValue().getAsFloat()));
						break;
					}
				}
			}
		}
	}

//	private List<ArThing> createArThings(int[] ids, OverlayType ovarlaytypes[], String[] urls, float[] scales, String[] txts){
//		List<ArThing> arThings = new ArrayList<ArThing>();
//		for (int i= 0; i< ids.length; i++) {
//			List<ArKvpString> strings = new ArrayList<ArKvpString>();
//			List<ArKvpFloat> floats = new ArrayList<ArKvpFloat>();
//			List<ArKvpInteger> integers = new ArrayList<ArKvpInteger>();
//
//			strings.add(new ArKvpString("model", urls[i]));
//			strings.add(new ArKvpString("label", txts[i]));
//			floats.add(new ArKvpFloat("scale", scales[i]));						
//			ArThing arThing = new ArThing(ids[i], ovarlaytypes[i], strings, integers, floats);
//			arThings.add(arThing);
//		}
//		return arThings;
//	}


    private ArMarkerdetector arFilter;

    private void pose(WebSocketSession session, JsonObject jsonMessage) {
	try {
	    System.err.println("json POSE from browser");
	    
	    String json = jsonMessage.getAsJsonPrimitive("pose").getAsString();
	    System.err.println("json:\n" + json);
	    JsonObject jsonObjects = gson.fromJson(json, JsonObject.class);

	    JsonArray  jsonArray = jsonObjects.getAsJsonArray("pose");
	    Iterator<JsonElement> itr = jsonArray.iterator();
	    while(itr.hasNext()){
		JsonElement jsonElm = itr.next();		
		if(jsonElm.isJsonNull()){
		    System.err.println("Really Skip null");
		    continue;
		}
		System.err.println("Got: " + jsonElm);
		
		JsonObject	jsonObject = jsonElm.getAsJsonObject();	
		int id = jsonObject.get("id").getAsInt();
		int type = jsonObject.get("type").getAsInt();
		//String id = jsonObject.get("id").getAsString();
		//String type = jsonObject.get("type").getAsString();
		float value = jsonObject.get("value").getAsFloat();		
		System.err.println("" + id + "#" + type + "#" + value);
		if(arFilter != null){
		    arFilter.setPose(id, type, value);
		}
		else{
		    System.err.println("Start the filter first");
		}
	    }	   
	}
	catch (Throwable t) {
	    t.printStackTrace();
		sendError(session, t.getMessage());
	}
    }

	private void reload(WebSocketSession session, JsonObject jsonMessage) {
	    try {
		System.err.println("json RELOAD from browser");
		if(arFilter != null){
		    arFilter.setArThing(createArThings(jsonMessage.getAsJsonPrimitive("augmentables").getAsString()));
		}	
	    }
	    catch (Throwable t) {
		t.printStackTrace();
		sendError(session, t.getMessage());
	    }
	}


    
	private void start(final WebSocketSession session, JsonObject jsonMessage) {
		try {
			UserSession user = new UserSession();
			MediaPipeline pipeline = kurento.createMediaPipeline();
			System.err.println("STATS A:" + pipeline.getLatencyStats());
			pipeline.setLatencyStats(true);
			System.err.println("STATS B:" + pipeline.getLatencyStats());
			user.setMediaPipeline(pipeline);
			//pipelines.put(session.getId(), pipeline);
			webRtcEndpoint = new WebRtcEndpoint.Builder(pipeline).build();
			
			user.setWebRtcEndpoint(webRtcEndpoint);
			users.put(session.getId(), user);

			// ICE candidates
			webRtcEndpoint.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {
				@Override
				public void onEvent(OnIceCandidateEvent event) {
					JsonObject response = new JsonObject();
					response.addProperty("id", "iceCandidate");
					response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
					try {
						synchronized (session) {
							session.sendMessage(new TextMessage(response.toString()));
						}
					} catch (IOException e) {
						log.debug(e.getMessage());
					}
				}
			});

			arFilter = new ArMarkerdetector.Builder(pipeline).build();
			if(jsonFile == null){
				System.err.println("json from browser");
				arFilter.setArThing(createArThings(jsonMessage.getAsJsonPrimitive("augmentables").getAsString()));	
			}
			else{
				System.err.println("json from file");
				arFilter.setArThing(createArThings(getFile(jsonFile)));	
			}
															
			arFilter.enableTickEvents(false);
			arFilter.enableAugmentation(true);
			arFilter.setMarkerPoseFrequency(false, 1);
			arFilter.setMarkerPoseFrameFrequency(false, 10);
			arFilter.enableMarkerCountEvents(false);			
			arFilter.addMarkerCountListener(new EventListener<MarkerCountEvent>() {
				@Override
				public void onEvent(MarkerCountEvent event) {
					String result = String.format("Marker %d count:%d (diff:%d): {}", event.getMarkerId(), event.getMarkerCount(), event.getMarkerCountDiff());
					log.debug(result, event);
				}
			});

			arFilter.addTickListener(new EventListener<TickEvent>() {
				@Override
				public void onEvent(TickEvent event) {
				    //String result = String.format("Tick msg %s time:%d : {}", event.getMsg(), event.getTickTimestamp());
					//log.debug(result, event);
					smart(event.getMsg(), event.getTickTimestamp());
				}
			});

			arFilter.addMarkerPoseListener(
					new EventListener<MarkerPoseEvent>() {
						@Override
						public void onEvent(MarkerPoseEvent event){
							//Just print content of event

							log.debug("\nMarkerPoseEvent: " + event);
							log.debug("frameId: " + event.getSequenceNumber());
							//log.debug("timestamp: " + event.getTimestamp());							
							log.debug("width:" + event.getWidth() +  " height:" + event.getHeight());

							log.debug("matrixProjection:" + event.getMatrixProjection());												
							List poses = event.getMarkerPose();

							if(poses != null){																	
								for(int z=0; z<poses.size(); z++){
									org.kurento.jsonrpc.Props props = (org.kurento.jsonrpc.Props)poses.get(z);									
									for(org.kurento.jsonrpc.Prop prop : props){
										java.util.ArrayList<Float> list;
										switch(prop.getName()){
										case "matrixModelview":		
											list = (java.util.ArrayList<Float>)prop.getValue();
											log.debug("matrixModelview:" + list);
											break;
										case "markerId":
											log.debug("\n\nThe MarkerId = " + prop.getValue());
											break;
										default:
											break;
										}		
									}									
								}	
							}
							log.debug("Got MarkerPoseEvent: ", event);
						}});

			webRtcEndpoint.connect(arFilter);
			arFilter.connect(webRtcEndpoint);
			System.err.println("jsonMessage: " + jsonMessage);
			System.err.println("jsonMessage.get(sdpOffer): " + jsonMessage.get("sdpOffer"));
			String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
			String sdpAnswer = webRtcEndpoint.processOffer(sdpOffer);

			JsonObject response = new JsonObject();
			response.addProperty("id", "startResponse");
			response.addProperty("sdpAnswer", sdpAnswer);
			//session.sendMessage(new TextMessage(response.toString()));

			synchronized (session) {
				session.sendMessage(new TextMessage(response.toString()));
			}

			webRtcEndpoint.gatherCandidates();
			out = new PrintWriter("smart.txt");

		} catch (Throwable t) {
			t.printStackTrace();
			sendError(session, t.getMessage());
		}
	}

	private void sendError(WebSocketSession session, String message) {
		try {
			JsonObject response = new JsonObject();
			response.addProperty("id", "error");
			response.addProperty("message", message);
			session.sendMessage(new TextMessage(response.toString()));
		} catch (IOException e) {
			log.error("Exception sending message", e);
		}
	}


	public void setJson(String jsonFile) {
		this.jsonFile = jsonFile;		
	}	

    private void smart(String msg, double time){
	out.println(msg + (int)time);
	out.flush();
	System.err.println(msg + "#" + time);
    }

    private void getStats(WebSocketSession session)
    {
    	
    	try {
    		Map<String,Stats> wr_stats= webRtcEndpoint.getStats();
		//System.err.println("GET STATS..." + wr_stats);
    		for (Stats s :  wr_stats.values()) {
		    //System.err.println("STATS:" + s);    		
    			switch (s.getType()) {		
    			case endpoint:{
			    //System.err.println("STATS endpoint");
    				EndpointStats end_stats= (EndpointStats) s;
    				double  e2eVideLatency= end_stats.getVideoE2ELatency() / 1000000;
    				
				smart("***SMART E2E\t", e2eVideLatency);



    				JsonObject response = new JsonObject();
    				response.addProperty("id", "videoE2Elatency");
    				response.addProperty("message", e2eVideLatency);				

			synchronized (session) {
    				session.sendMessage(new TextMessage(response.toString()));				
			}
			}
    				break;
	
			case inboundrtp:{
			    RTCInboundRTPStreamStats stats = (RTCInboundRTPStreamStats)s;
			    //System.err.println(stats.getJitter());
			}
			    break;
			case outboundrtp:{
			    RTCOutboundRTPStreamStats stats = (RTCOutboundRTPStreamStats)s;
			    //  System.err.println(stats.getRoundTripTime());

    			// 	JsonObject response = new JsonObject();
    			// 	response.addProperty("id", "videoE2Elatency");
    			// 	response.addProperty("message", stats.getRoundTripTime());

			// synchronized (session) {
    			// 	session.sendMessage(new TextMessage(response.toString()));				
			// }
			}
			    break;

    			default:	
			    //System.err.println("STATS DEFAULTS: " + s.getType() + "#" + s.getClass());
    				break;
    			}				
    		}
    	} catch (IOException e) {
			log.error("Exception sending message", e);
		}
    	
    }
}
