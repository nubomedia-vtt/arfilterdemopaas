/*
 * (C) Copyright 2016 NUBOMEDIA (http://www.nubomedia.eu)
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
package fi.vtt.nubomedia.armodule;

import org.kurento.client.EventListener;
import org.kurento.client.FaceOverlayFilter;
import org.kurento.client.IceCandidate;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.OnIceCandidateEvent;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.google.gson.JsonObject;

/**
 * User session.
 * 
 * @author Boni Garcia (boni.garcia urjc.es)
 * @since 6.4.1
 */
public class UserSession {
    private final Logger log = LoggerFactory.getLogger(UserSession.class);    
    private ModuleHandler handler;
    private WebRtcEndpoint webRtcEndpoint;
    private MediaPipeline mediaPipeline;
    private KurentoClient kurentoClient;
    private String sessionId;
    private Object userData;

    public void setUserData(Object userData){
	this.userData = userData;
    }

    public Object getUserData(){
	return userData;
    }
    
    public UserSession(final String sessionId, ModuleHandler handler) {
	this.sessionId = sessionId;
	this.handler = handler;
    }

    public WebRtcEndpoint getWebRtcEndpoint(){
	return webRtcEndpoint;
    }
    
    public MediaPipeline getMediaPipeline(){
	return mediaPipeline;
    }

    public String getSessionId(){
	return sessionId;
    }
    
    public String startSession(final WebSocketSession session, String sdpOffer, JsonObject jsonMessage) {
	System.err.println("ME USER SESSION START SESSION");

	// One KurentoClient instance per session
	kurentoClient = KurentoClient.create();
	log.info("Created kurentoClient (session {})", sessionId);
	
	// Media logic (pipeline and media elements connectivity)
	mediaPipeline = kurentoClient.createMediaPipeline();
	log.info("Created Media Pipeline {} (session {})", mediaPipeline.getId(), session.getId());
	
	mediaPipeline.setLatencyStats(true);
	log.info("Media Pipeline {} latencyStants set{}", mediaPipeline.getId(), mediaPipeline.getLatencyStats());
	
	webRtcEndpoint = new WebRtcEndpoint.Builder(mediaPipeline).build();
	handler.createPipeline(this, jsonMessage);
	
	// WebRTC negotiation
	webRtcEndpoint.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {
		@Override
		    public void onEvent(OnIceCandidateEvent event) {
		    JsonObject response = new JsonObject();
		    response.addProperty("id", "iceCandidate");
		    response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
		    handler.sendMessage(session, new TextMessage(response.toString()));
		}
	    });
	String sdpAnswer = webRtcEndpoint.processOffer(sdpOffer);
	webRtcEndpoint.gatherCandidates();
	
	return sdpAnswer;
    }
    
    public void addCandidate(JsonObject jsonCandidate) {
	IceCandidate candidate = new IceCandidate(jsonCandidate.get("candidate").getAsString(),
						  jsonCandidate.get("sdpMid").getAsString(), jsonCandidate.get("sdpMLineIndex").getAsInt());
	webRtcEndpoint.addIceCandidate(candidate);
    }
    
    public void release() {
	log.info("Releasing media pipeline {} (session {})", mediaPipeline.getId(), sessionId);
	mediaPipeline.release();
	
	log.info("Destroying kurentoClient (session {})", sessionId);
	kurentoClient.destroy();
    }
}
