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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import org.springframework.boot.CommandLineRunner;

/**
 * @author Markus Ylikerala
 */
@SpringBootApplication
@EnableWebSocket
public class Ar3DApp implements WebSocketConfigurer, CommandLineRunner{

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

    //	@Bean
    //	public KurentoClient kurentoClient() {
    //	    return KurentoClient.create();
	    //return KurentoClient.create(System.getProperty("kms.ws.uri",
	    //			DEFAULT_KMS_WS_URI));
    //	}

    @Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(handler(), "/ar3d");
	}

	public static void main(String[] args) throws Exception {
		new SpringApplication(Ar3DApp.class).run(args);
	}
}
