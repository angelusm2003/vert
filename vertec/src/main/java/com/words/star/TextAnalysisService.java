package com.words.star;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;

import java.util.ArrayList;
import java.util.List;

public class TextAnalysisService extends AbstractVerticle {

  private PgPool pgPool;

  @Override
  public void start(Future<Void> future) {
    initializeDatabase();

    Router router = Router.router(vertx);
    router.route("/analyze*").handler(BodyHandler.create());
    router.post("/analyze").handler(this::analyzeText);

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(8080, result -> {
        if (result.succeeded()) {
          future.isComplete();
           // .complete();
          System.out.println("Server started on port 8080");
        } else {
          future.failed();
          //future.fail(result.cause());
        }
      });
  }

  private void initializeDatabase() {
    PgConnectOptions connectOptions = new PgConnectOptions()
      .setHost("localhost")
      .setPort(5432)
      .setDatabase("words")
      .setUser("postgres")
      .setPassword("123456");

    PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

    pgPool = PgPool.pool(vertx, connectOptions, poolOptions);
  }

  private void analyzeText(RoutingContext routingContext) {
    JsonObject requestBody = routingContext.getBodyAsJson();
    String text = requestBody.getString("text");

    WebClientOptions options = new WebClientOptions()
      .setDefaultHost("localhost")
      .setDefaultPort(8080);

    WebClient client = WebClient.create(vertx, options);

    client.get("/analyze")
      .as(BodyCodec.jsonObject())
      .send(ar -> {
        if (ar.succeeded()) {
          HttpResponse<JsonObject> response = ar.result();
          JsonObject wordCounts = response.body();

          String closestValueWord = findClosestValueWord(text, wordCounts);
          String closestLexicalWord = findClosestLexicalWord(text, wordCounts);

          JsonObject jsonResponse = new JsonObject()
            .put("value", closestValueWord)
            .put("lexical", closestLexicalWord);

          HttpServerResponse httpServerResponse = routingContext.response();
          httpServerResponse.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
          httpServerResponse.end(jsonResponse.encode());
        } else {
          routingContext.fail(ar.cause());
        }
      });
  }

  private String findClosestValueWord(String text, JsonObject wordCounts) {
    List<String> words = new ArrayList<>(wordCounts.fieldNames());

    return words.stream()
      .min((word1, word2) -> {
        int valueDifference = calculateValue(text, word1) - calculateValue(text, word2);
        if (valueDifference == 0) {
          return word2.compareTo(word1);
        }
        return valueDifference;
      })
      .orElse(null);
  }

  private String findClosestLexicalWord(String text, JsonObject wordCounts) {
    List<String> words = new ArrayList<>(wordCounts.fieldNames());

    return words.stream()
      .min((word1, word2) -> {
        int lexicalDifference = Math.abs(text.compareToIgnoreCase(word1)) - Math.abs(text.compareToIgnoreCase(word2));
        if (lexicalDifference == 0) {
          return word1.compareTo(word2);
        }
        return lexicalDifference;
      })
      .orElse(null);
  }

  private int calculateValue(String text, String word) {
    int value = 0;
    for (char c : word.toCharArray()) {
      value += Character.toLowerCase(c) - 'a' + 1;
    }
    return Math.abs(text.length() - value);
  }

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new TextAnalysisService());
  }
}
