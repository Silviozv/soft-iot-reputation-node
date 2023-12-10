package reputation.node.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.StringReader;

/**
 *
 * @author Allan Capistrano
 * @version 1.0.0
 */
public final class JsonStringToJsonObject {

  /**
   * Converte um JSON que está no formato de String em um JSON Object da
   * biblioteca gson.
   *
   * @param jsonInString String - JSON no formato de String
   * @return JsonObject
   */
  public static JsonObject convert(String jsonInString) {
    JsonReader reader = new JsonReader(new StringReader(jsonInString));

    reader.setLenient(true);

    JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();

    return jsonObject;
  }
}
