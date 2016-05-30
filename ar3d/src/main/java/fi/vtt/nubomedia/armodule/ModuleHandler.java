package fi.vtt.nubomedia.armodule;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import com.google.gson.JsonObject;

/**
 * User session.
 * 
 * @author Markus Ylikerala
 * @since 6.4.1
 */
public interface ModuleHandler{
    void createPipeline(UserSession userSEssion, JsonObject jsonMessage);
    void sendMessage(WebSocketSession session, TextMessage message);
}
