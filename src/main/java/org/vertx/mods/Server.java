package org.vertx.mods;

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Verticle;

public class Server extends Verticle {

	public EventBus eb;
	public String body = "hello";

	public void start() {
		HttpServer server = vertx.createHttpServer();
		eb = vertx.eventBus();

		JsonObject authConfig = new JsonObject();
		authConfig.putString("address", "test.auth");
		authConfig.putString("users_table", "users");
		authConfig.putString("database", "auth_db");
		authConfig.putString("username_db", "root");
		authConfig.putString("password_db", "JHJD89373");
		authConfig.putNumber("session_timeout", 900000);
		
		
		JsonObject mysqlConfig = new JsonObject();
		mysqlConfig.putString("address", "test.mysql");
		mysqlConfig.putString("database", "auth_db");
		mysqlConfig.putString("username", "root");
		mysqlConfig.putString("password", "JHJD89373");
		mysqlConfig.putString("connection", "MySQL");
		container.deployModule("io.vertx~mod-mysql-postgresql~0.3.0-SNAPSHOT", mysqlConfig);
		server.requestHandler(new Handler<HttpServerRequest>() {
			public void handle(HttpServerRequest request) {
				JsonObject query = new JsonObject();
				query.putString("username", "tim");
				query.putString("password", "foo");
				
//				query.putString("action", "prepared");
//				query.putString("statement" , "SELECT * FROM users WHERE username=? AND password=?");
//				JsonArray array = new JsonArray();
//				array.addString("tim");
//				array.addString("foo");
//				query.putArray("values", array);
				String table = "users";
				String username = "tim";
				String password = "foo";
				String QUERY = "SELECT * FROM " + table + " WHERE username='"+username+ "' AND password='"+password+"';";
				
				JsonObject findMsg = new JsonObject().putString("action", "raw")
						.putString("command", QUERY);
				eb.send("test.mysql", findMsg,
						new Handler<Message<JsonObject>>() {

							@Override
							public void handle(Message<JsonObject> reply) {
								// TODO Auto-generated method stub
								body = reply.body().toString();
							}
						});
				request.response().end(body);
			}
		});

		server.listen(8000, "localhost");
	}
}
