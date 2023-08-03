package br.uefs.larsid.iot.soft.models.conducts;

import br.uefs.larsid.iot.soft.enums.ConductType;
import java.util.logging.Logger;

public abstract class Conduct {

  private ConductType conductType;
  private static final Logger logger = Logger.getLogger(
    Conduct.class.getName()
  );

  public Conduct() {}

  /**
   * Define o comportamento do nó.
   */
  public abstract void defineConduct();

  /**
   * Avalia o serviço que foi prestado pelo dispositivo, de acordo com o tipo de
   * comportamento do nó.
   */
  public void evaluateDevice() {
    switch (this.conductType) {
      case HONEST:
        logger.info("Provided the service.");
        break;
      case MALICIOUS:
        logger.info("Did not provide the service.");
        break;
      case SELFISH:
        logger.info("TODO");
        break;
      case CORRUPT:
        logger.info("TODO");
        break;
      case DISTURBING:
        logger.info("TODO");
        break;
      default:
        logger.severe("Error! ConductType not found.");
        break;
    }
  }

  public ConductType getConductType() {
    return conductType;
  }

  public void setConductType(ConductType conductType) {
    this.conductType = conductType;
  }
}
