package reputation.node.enums;

/**
 * Enumerador com os tipos possíveis de serviços que um nó pode prestar.
 *
 * @author Allan Capistrano
 * @version 1.1.0
 */
public enum NodeServiceType {
  THERMOMETER("Thermometer"),
  HUMIDITY_SENSOR("HumiditySensor"),
  PULSE_OXYMETER("PulseOxymeter"),
  WIND_DIRECTION_SENSOR("WindDirectionSensor");

  private String description;

  NodeServiceType(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}
