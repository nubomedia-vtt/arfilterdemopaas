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
package fi.vtt.nubomedia.kurento;

import org.kurento.client.KurentoClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

//import org.kurento.client.factory.KurentoClient;
import org.kurento.client.KurentoClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * @author Markus Ylikerala
 */
@Configuration
@EnableWebSocket
@EnableAutoConfiguration
public class Ar3DApp implements WebSocketConfigurer, CommandLineRunner{

    static final String DEFAULT_APP_SERVER_URL = "https://localhost:8080";
    //static final String DEFAULT_APP_SERVER_URL = "https://localhost:8443";

    //final static String DEFAULT_KMS_WS_URI = "ws://localhost:8888/kurento";
    //final static String DEFAULT_APP_SERVER_URL = "http://localhost:8080";

	private Ar3DHandler ar3DHandler;

	@Bean
	public Ar3DHandler handler() {	
		ar3DHandler = new Ar3DHandler();
		return ar3DHandler;
	}

	public void run(String... args) {
		for(String arg : args){			
			ar3DHandler.setJson(arg);
		}
	}

	@Bean
	public KurentoClient kurentoClient() {
	    return KurentoClient.create();
	    //return KurentoClient.create(System.getProperty("kms.ws.uri",
	    //			DEFAULT_KMS_WS_URI));
	}

    @Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(handler(), "/ar3d");
	}

	public static void main(String[] args) throws Exception {
		new SpringApplication(Ar3DApp.class).run(args);
	}
}
