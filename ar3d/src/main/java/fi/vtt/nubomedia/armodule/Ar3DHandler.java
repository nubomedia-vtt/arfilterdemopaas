/**
Licensing and distribution

ArModule is licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

ALVAR 2.0.0 - A Library for Virtual and Augmented Reality Copyright 2007-2012 VTT Technical Research Centre of Finland Licensed under the GNU Lesser General Public License

Irrlicht Engine, the zlib and libpng. The Irrlicht Engine is based in part on the work of the Independent JPEG Group The module utilizes IJG code when the Irrlicht engine is compiled with support for JPEG images.
*/

/** @author Markus Ylikerälä */

package fi.vtt.nubomedia.armodule;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.kurento.client.EventListener;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;

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

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.DefaultRealMatrixChangingVisitor;
import org.apache.commons.math3.linear.MatrixUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * @author Markus Ylikerala 
 */
public class Ar3DHandler extends BaseHandler{	
    private final Logger log = LoggerFactory.getLogger(Ar3DHandler.class);
    private static final Gson gson = new GsonBuilder().create();
    private String jsonFile;
   
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message)
	throws Exception {
	
	System.err.println("ME HANLE TEXT MSG");
	
	JsonObject jsonMessage = gson.fromJson(message.getPayload(),
					       JsonObject.class);
	
	System.err.println("ME HANLE TEXT MSG: #" + jsonMessage.get("id").getAsString() + "#");
	
	log.debug("Incoming message: {}", jsonMessage);
	
	switch (jsonMessage.get("id").getAsString()) {
	case "reload":
	    reload(session, jsonMessage);
	    break;
	case "pose":
	    pose(session, jsonMessage);
	    break;
	default:
	    super.handleTextMessage(session, message);
	    break;
	}
    }

    public void createPipeline(UserSession userSession, JsonObject jsonMessage){
	try{
	    System.err.println("ME CREATE PIPELINE");

	MediaPipeline mediaPipeline = userSession.getMediaPipeline();
	ArMarkerdetector arFilter = new ArMarkerdetector.Builder(mediaPipeline).build();
	
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
	
	addModuleListeners(arFilter, userSession.getSessionId());
	
	WebRtcEndpoint webRtcEndpoint = userSession.getWebRtcEndpoint();
	webRtcEndpoint.connect(arFilter);
	arFilter.connect(webRtcEndpoint);
	
	userSession.setUserData(arFilter);
	}
	catch(Exception e){
	    System.err.println(e);
	    throw new RuntimeException(e);
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
	System.err.println("*createARThings:#" + jsonObjects + "#");
	List<ArThing> arThings = new ArrayList<ArThing>();
	JsonArray  jsonArray = jsonObjects.getAsJsonArray("augmentables");
	
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
	System.err.println("*ResultArThigs: " + arThings.size() + "#" + arThings);
	return arThings;
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
    
    private ArMarkerdetector getArFilter(WebSocketSession session){
	UserSession user = users.get(session.getId());
	if (user == null) {
	    log.error("No UserSession found with the sessionId{}", session.getId());
	    return null;
	}
	    
	ArMarkerdetector arFilter = (ArMarkerdetector)user.getUserData();
	if(arFilter == null){
	    log.error("No ArFilter found with the sessionId{}", session.getId());
	    return null;
	}
	return arFilter;
    }

    private void pose(WebSocketSession session, JsonObject jsonMessage) {
	try {
	    System.err.println("json POSE from browser");

	    ArMarkerdetector arFilter = getArFilter(session);
	    if(arFilter == null){
		log.error("Start the filter first");
		return;
	    }

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

		arFilter.setPose(id, type, value);
	    }	   
	}
	catch (Throwable t) {
	    t.printStackTrace();
		error(session, t.getMessage());
	}
    }

    private void reload(WebSocketSession session, JsonObject jsonMessage) {
	try {
	    System.err.println("json RELOAD from browser");
	    ArMarkerdetector arFilter = getArFilter(session);
	    if(arFilter == null){
		log.error("Start the filter first");
		return;
	    }
	    arFilter.setArThing(createArThings(jsonMessage.getAsJsonPrimitive("augmentables").getAsString()));
	}
	catch (Throwable t) {
	    t.printStackTrace();
	    error(session, t.getMessage());
	}
    }
    
    private void addModuleListeners(ArMarkerdetector arFilter, final String id){
	arFilter.addTickListener(new EventListener<TickEvent>() {
		@Override
		    public void onEvent(TickEvent event) {
		    //String result = String.format("Tick msg %s time:%d : {}", event.getMsg(), event.getTickTimestamp());
		    //log.debug(result, event);
		    smart(event.getMsg(), event.getTickTimestamp(), id);
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
	
    }
    
    public void setJson(String jsonFile) {
	this.jsonFile = jsonFile;		
    }	
}
