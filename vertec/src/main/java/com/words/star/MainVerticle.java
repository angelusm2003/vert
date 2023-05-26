package com.words.star;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MainVerticle extends AbstractVerticle {

  private PgPool pgPool;

  @Override
  public void start(Promise<Void> startPromise) {
    try {
      // Set up the PostgreSQL connection pool
      PgConnectOptions connectOptions = new PgConnectOptions()
        .setHost("localhost")
        .setPort(5432)
        .setDatabase("words")
        .setUser("postgres")
        .setPassword("123456");

      PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

      pgPool = PgPool.pool(vertx, connectOptions, poolOptions);

      // Set up the Vert.x router
      Router router = Router.router(vertx);
      router.route().handler(BodyHandler.create());
      router.post("/analyze").handler(this::analyzeText);

      // Deploy the HTTP server
      vertx.createHttpServer()
        .requestHandler(router)
        .listen(8089, ar -> {
          if (ar.succeeded()) {
            System.out.println("Server started on port 8089");
            startPromise.complete();
          } else {
            System.out.println("Server failed to start: " + ar.cause());
            startPromise.fail(ar.cause());
          }
        });
    } catch (Exception e) {
      System.err.println("Failed to start the server: " + e.getMessage());
      startPromise.fail(e);
    }
  }

  private void analyzeText(RoutingContext routingContext) {
    try {
      String text = routingContext.getBodyAsJson().getString("text");

      // Prepare the SQL statement
      String sql = "SELECT * FROM phrases";

      // Query the database
      pgPool.query(sql)
        .execute(ar -> {
          if (ar.succeeded()) {
            RowSet<Row> rows = ar.result();
            List<String> words = new ArrayList<>();
            rows.forEach(row -> words.add(row.getString("phrase")));

            JsonObject result = new JsonObject();
            String closestValue = getClosestValue(text, words);
            String closestLexical = getClosestLexical(text, words);
            result.put("value", closestValue);
            result.put("lexical", closestLexical);

            saveTextInDatabase(text);

            routingContext.response()
              .putHeader("Content-Type", "application/json")
              .end(result.encode());
          } else {
            routingContext.response().setStatusCode(500).end("Internal Server Error");
          }
        });
    } catch (Exception e) {
      System.err.println("Failed to analyze text: " + e.getMessage());
      routingContext.response().setStatusCode(500).end("Internal Server Error");
    }
  }

  private String getClosestValue(String text, List<String> words) {
    if (words.isEmpty()) {
      return null;
    }

    int closestValue = Integer.MAX_VALUE;
    String closestWord = null;

    for (String word : words) {
      int wordValue = calculateCharacterValue(word);
      int textValue = calculateCharacterValue(text);
      int difference = Math.abs(wordValue - textValue);
      if (difference < closestValue) {
        closestValue = difference;
        closestWord = word;
      } else if (difference == closestValue) {
        if (wordValue > calculateCharacterValue(closestWord)) {
          closestWord = word;
        } else if (wordValue == calculateCharacterValue(closestWord)) {
          closestWord = getLexicalClosest(word, closestWord);
        }
      }
    }

    return closestWord;
  }

  private int calculateCharacterValue(String word) {
    int value = 0;
    for (char c : word.toCharArray()) {
      value += c - 'a' + 1;
    }
    return value;
  }

  private String getLexicalClosest(String word1, String word2) {
    return word1.compareTo(word2) < 0 ? word1 : word2;
  }

  private String getClosestLexical(String text, List<String> words) {
    if (words.isEmpty()) {
      return null;
    }

    String closestWord = null;

    for (String word : words) {
      if (closestWord == null || word.compareTo(text) < 0 && word.compareTo(closestWord) > 0) {
        closestWord = word;
      }
    }

    return closestWord;
  }

  private void saveTextInDatabase(String text) {
    // Prepare the SQL statement
    String sql = "INSERT INTO phrases (phrase) VALUES ($1)";

    // Execute the insert query
    pgPool.preparedQuery(sql)
      .execute(Tuple.of(text), ar -> {
        if (ar.failed()) {
          System.err.println("Failed to save text in the database: " + ar.cause());
        }
      });
  }

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new MainVerticle());
  }
}
