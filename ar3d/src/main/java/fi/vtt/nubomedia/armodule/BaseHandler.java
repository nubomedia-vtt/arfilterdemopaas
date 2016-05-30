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
package fi.vtt.nubomedia.armodule;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.kurento.client.internal.NotEnoughResourcesException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import com.google.gson.Gson;
import java.io.PrintWriter;
import org.kurento.client.EndpointStats;
import org.kurento.client.Stats;
import java.util.Map;

/**
 * base handler
 * 
 * @author Markus Ylikerala
 */
public abstract class BaseHandler extends TextWebSocketHandler implements ModuleHandler{	
    private final Logger log = LoggerFactory.getLogger(BaseHandler.class);
    protected final ConcurrentHashMap<String, UserSession> users = new ConcurrentHashMap<String, UserSession>();
    private static final Gson gson = new GsonBuilder().create();
    private PrintWriter out;
    
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
	case "stop":
	    release(session);
	    break;
	case "onIceCandidate": 
	    onIceCandidate(session, jsonMessage);
	    break;
	default:
	    error(session,
		  "Invalid message with id "
		  + jsonMessage.get("id").getAsString());
	    break;
	}
    }

    private void start(final WebSocketSession session, JsonObject jsonMessage) {
	try {
	    UserSession user = new UserSession(session.getId(), this);
	    users.put(session.getId(), user);

	    try{
		out = new PrintWriter("smart.txt");	
	    }
	    catch(Throwable t){
		t.printStackTrace();
	    }
	    String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
	    String sdpAnswer = user.startSession(session, sdpOffer, jsonMessage);
	    
	    JsonObject response = new JsonObject();
	    response.addProperty("id", "startResponse");
	    response.addProperty("sdpAnswer", sdpAnswer);
	    sendMessage(session, new TextMessage(response.toString()));	    
	} catch (Throwable t) {
	    t.printStackTrace();
	    error(session, t.getMessage());
	}
    }

    private void onIceCandidate(WebSocketSession session, JsonObject jsonMessage) {
	JsonObject jsonCandidate = jsonMessage.get("candidate").getAsJsonObject();
	UserSession user = users.get(session.getId());
	System.err.println("ME onIce: " + user);
	if (user != null) {
	    user.addCandidate(jsonCandidate);
	}
    }

    private void notEnoughResources(WebSocketSession session) {
	// Send notEnoughResources message to client
	JsonObject response = new JsonObject();
	response.addProperty("id", "notEnoughResources");
	sendMessage(session, new TextMessage(response.toString()));

	// Release media session
	release(session);
    }

    protected void error(WebSocketSession session, String message) {
	try {
	    JsonObject response = new JsonObject();
	    response.addProperty("id", "error");
	    response.addProperty("message", message);
	    sendMessage(session, new TextMessage(response.toString()));
	    release(session);
	} catch (Exception e) {
	    log.error("Exception sending message", e);
	}
    }

    private void release(WebSocketSession session) {
	UserSession user = users.remove(session.getId());
	if (user != null) {
	    user.release();
	}
    }

    public void sendMessage(WebSocketSession session, TextMessage message) {
	try {
	    log.info("Sending message {} in session {}", message.getPayload(), session.getId());
	    synchronized (session) {
		session.sendMessage(message);
	    }

	} catch (IOException e) {
	    log.error("Exception sending message", e);
	}
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
	log.info("Closed websocket connection of session {}", session.getId());
	release(session);
    }
    
    protected void smart(String msg, double time, String id){
	try{
	    out.println(msg + (int)time + "\t" + id);
	    out.flush();
	    System.err.println(msg + "#" + time + "\t" + id);
	}
	catch(Throwable t){
	    t.printStackTrace();
	}
    }
    
    private void getStats(WebSocketSession session){	
    	try {
	UserSession user = users.get(session.getId());
	if (user == null) {
	    return;
	}
	    
	Map<String,Stats> wr_stats = user.getWebRtcEndpoint().getStats();
	//System.err.println("GET STATS..." + user.getWebRtcEndpoint().getStats());
	System.err.println("GET STATS..." + wr_stats);
	for (Stats s :  wr_stats.values()) {
	//for (Stats s :  user.getWebRtcEndpoint().getStats().values()) {
		//System.err.println("STATS:" + s);    		
		switch (s.getType()) {		
		case endpoint:
		    //System.err.println("STATS endpoint");
		    EndpointStats end_stats= (EndpointStats) s;
		    double  e2eVideLatency= end_stats.getVideoE2ELatency() / 1000000;
		    
		    smart("***SMART E2E\t", e2eVideLatency, session.getId());
		    		    		    
		    JsonObject response = new JsonObject();
		    response.addProperty("id", "videoE2Elatency");
		    response.addProperty("message", e2eVideLatency);				
		    
		    sendMessage(session, new TextMessage(response.toString()));				
		    break;
		    
		    //case inboundrtp:{
		    //RTCInboundRTPStreamStats stats = (RTCInboundRTPStreamStats)s;
		    //System.err.println(stats.getJitter());
		    //}
		    //break;
		    //case outboundrtp:{
		    //RTCOutboundRTPStreamStats stats = (RTCOutboundRTPStreamStats)s;
		    //  System.err.println(stats.getRoundTripTime());
		    
		    // 	JsonObject response = new JsonObject();
		    // 	response.addProperty("id", "videoE2Elatency");
		    // 	response.addProperty("message", stats.getRoundTripTime());
		    
		    // synchronized (session) {
		    // 	session.sendMessage(new TextMessage(response.toString()));				
		    // }
		    //}
		    //break;
		    
		default:	
		    //System.err.println("STATS DEFAULTS: " + s.getType() + "#" + s.getClass());
		    break;
		}				
	    }
	} catch (Throwable e) {
	    log.error("Exception stats", e);
	}
    	
    }
}
